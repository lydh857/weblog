package com.blog.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.security.sensitive.SensitiveWordService;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.dto.CreateCommentRequest;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.mapper.CommentMapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 评论服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveWordService sensitiveWordService;
    private final AiReviewService aiReviewService;

    /** Redis key: 文章评论数 */
    private static final String KEY_COMMENT_COUNT = "post:comment:";
    private static final String KEY_COMMENT_DIRTY = "post:comment:dirty";

    /**
     * 发表评论
     * @param auditEnabled 是否开启审核（true=新评论为 pending，false=直接 approved）
     */
    public Comment createComment(Long userId, CreateCommentRequest req, boolean auditEnabled) {
        // 校验父评论
        if (req.getParentId() != null && req.getParentId() > 0) {
            Comment parent = commentMapper.selectById(req.getParentId());
            if (parent == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "父评论不存在");
            }
            if (!parent.getPostId().equals(req.getPostId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父评论不属于该文章");
            }
            // 直接回复一级评论时，不记录 replyToUserId（避免冗余显示"@用户"）
            // 判断依据：replyToUserId 为空，或等于一级评论作者本人
            if (parent.getParentId() == 0L
                    && (req.getReplyToUserId() == null || req.getReplyToUserId().equals(parent.getUserId()))) {
                req.setReplyToUserId(null);
            }
        }

        Comment comment = new Comment();
        comment.setPostId(req.getPostId());
        comment.setUserId(userId);
        comment.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
        comment.setReplyToUserId(req.getReplyToUserId());
        String cleanedContent = Jsoup.clean(req.getContent(), Safelist.none()).trim();
        if (cleanedContent.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评论内容不能为空");
        }
        if (sensitiveWordService.containsSensitiveWord(cleanedContent)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评论内容包含敏感词，请修改后提交");
        }
        comment.setContent(cleanedContent);
        comment.setLikeCount(0);
        // 根据审核开关决定评论初始状态
        comment.setStatus(auditEnabled ? "pending" : "approved");
        comment.setIsTop(false);
        commentMapper.insert(comment);

        // 审核关闭时直接计数，开启审核时不计数（审核通过后再计）
        if (!auditEnabled) {
            redisTemplate.opsForValue().increment(KEY_COMMENT_COUNT + req.getPostId());
            redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, req.getPostId().toString());
        }

        log.info("评论发表: userId={}, postId={}, commentId={}", userId, req.getPostId(), comment.getId());

        // 异步触发 AI 审核（不阻塞评论提交）
        try {
            aiReviewService.reviewComment(comment.getId(), null);
        } catch (Exception e) {
            log.warn("触发 AI 审核失败，不影响评论提交: {}", e.getMessage());
        }

        return comment;
    }

    /**
     * 删除评论（只能删自己的）
     */
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己的评论");
        }
        commentMapper.deleteById(commentId);

        RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + comment.getPostId());
        redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, comment.getPostId().toString());

        log.info("评论删除: userId={}, commentId={}", userId, commentId);
    }

    /**
     * 批量删除评论（只能删自己的）
     */
    @Transactional
    public void batchDeleteComments(Long userId, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }

        List<Long> uniqueCommentIds = commentIds.stream().distinct().toList();
        List<Comment> comments = commentMapper.selectByIds(uniqueCommentIds);
        Map<Long, Comment> commentMap = new HashMap<>();
        for (Comment comment : comments) {
            if (comment != null && comment.getId() != null) {
                commentMap.putIfAbsent(comment.getId(), comment);
            }
        }

        Map<Long, Long> decrementByPostId = new HashMap<>();
        for (Long commentId : uniqueCommentIds) {
            Comment comment = commentMap.get(commentId);
            if (comment == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
            }
            if (!comment.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能删除自己的评论");
            }
            decrementByPostId.merge(comment.getPostId(), 1L, Long::sum);
        }

        commentMapper.deleteByIds(uniqueCommentIds);
        for (Map.Entry<Long, Long> entry : decrementByPostId.entrySet()) {
            Long postId = entry.getKey();
            Long delta = entry.getValue();
            RedisCounterUtil.safeDecrementBy(redisTemplate, KEY_COMMENT_COUNT + postId, delta);
            redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, postId.toString());
        }

        log.info("评论批量删除: userId={}, count={}", userId, uniqueCommentIds.size());
    }

    /**
     * 获取文章一级评论（分页）
     * @param sort "hot"=最热（点赞数降序），其他=最新（时间降序）
     */
    public IPage<Comment> getTopLevelComments(Long postId, int pageNum, int pageSize, String sort) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getParentId, 0L)
                .eq(Comment::getStatus, "approved")
                .orderByDesc(Comment::getIsTop);
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Comment::getLikeCount).orderByDesc(Comment::getCreateTime);
        } else {
            wrapper.orderByDesc(Comment::getCreateTime);
        }
        return commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 获取指定父评论的回复列表
     */
    public List<Comment> getReplies(List<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getParentId, parentIds)
                .eq(Comment::getStatus, "approved")
                .orderByAsc(Comment::getCreateTime));
    }

    /**
     * 分页获取指定父评论的回复列表
     */
    public IPage<Comment> getRepliesPage(Long parentId, int pageNum, int pageSize) {
        return commentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getParentId, parentId)
                        .eq(Comment::getStatus, "approved")
                        .orderByAsc(Comment::getCreateTime));
    }


    /**
     * 获取用户的评论列表（分页）
     */
    public IPage<Comment> getMyComments(Long userId, int pageNum, int pageSize) {
        return commentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .orderByDesc(Comment::getCreateTime));
    }

    /**
     * 根据ID批量查询评论
     */
    public List<Comment> getByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return commentMapper.selectByIds(ids);
    }

    /**
     * 获取有变更的文章评论数（供定时任务落库）
     */
    public Map<Long, Long> getDirtyCommentCounts() {
        Set<String> dirtyIds = redisTemplate.opsForSet().members(KEY_COMMENT_DIRTY);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (String idStr : dirtyIds) {
            Long postId = Long.parseLong(idStr);
            String val = redisTemplate.opsForValue().get(KEY_COMMENT_COUNT + postId);
            if (val != null) {
                result.put(postId, Long.parseLong(val));
            }
        }
        return result;
    }

    public void clearDirty(Long postId) {
        redisTemplate.opsForSet().remove(KEY_COMMENT_DIRTY, postId.toString());
    }

    /**
     * 获取文章评论数（Redis 优先，无值时从数据库初始化）
     */
    public long getCommentCount(Long postId) {
        String val = redisTemplate.opsForValue().get(KEY_COMMENT_COUNT + postId);
        if (val != null) return Long.parseLong(val);
        try {
            Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(comment_count, 0) FROM t_post WHERE id = ?", Integer.class, postId);
            long count = dbCount != null ? dbCount : 0;
            redisTemplate.opsForValue().set(KEY_COMMENT_COUNT + postId, String.valueOf(count));
            return count;
        } catch (Exception e) {
            log.warn("回填评论数失败: postId={}, {}", postId, e.getMessage());
            return 0;
        }
    }
}
