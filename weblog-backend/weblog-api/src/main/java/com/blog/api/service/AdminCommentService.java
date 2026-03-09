package com.blog.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.dto.CommentVO;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.mapper.CommentMapper;
import com.blog.system.entity.User;
import com.blog.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.blog.common.constant.CommonConstant.MAX_BATCH_SIZE;
import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 管理端评论服务 - 聚合 interaction/system/content 模块
 */
@Service
@RequiredArgsConstructor
public class AdminCommentService {

  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final PostMapper postMapper;
  private final StringRedisTemplate redisTemplate;

  private static final String KEY_COMMENT_COUNT = "post:comment:";
  private static final String KEY_COMMENT_DIRTY = "post:comment:dirty";
  private static final Set<String> VALID_COMMENT_STATUSES = Set.of("pending", "approved", "rejected", "spam");

  /**
   * 分页查询评论列表
   */
  public Map<String, Object> listComments(int pageNum, int pageSize,
                                           String status, Long postId,
                                           String postTitle, Boolean isTop) {
    pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);

    // 按文章标题搜索：先查匹配的 postId 集合
    Set<Long> matchedPostIds = null;
    if (postTitle != null && !postTitle.trim().isEmpty()) {
      List<Post> matchedPosts = postMapper.selectList(
          new LambdaQueryWrapper<Post>()
              .like(Post::getTitle, postTitle.trim())
              .select(Post::getId));
      matchedPostIds = matchedPosts.stream().map(Post::getId).collect(Collectors.toSet());
      if (matchedPostIds.isEmpty()) {
        return Map.of("records", List.of(), "total", 0L, "current", (long) pageNum, "pages", 0L);
      }
    }

    LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(Comment::getStatus, status);
    }
    if (postId != null) {
      wrapper.eq(Comment::getPostId, postId);
    } else if (matchedPostIds != null) {
      wrapper.in(Comment::getPostId, matchedPostIds);
    }
    if (isTop != null) {
      wrapper.eq(Comment::getIsTop, isTop);
    }
    // 待审核优先，然后按时间倒序
    // 注：此处 last() 内容为固定 SQL，不含用户输入，无注入风险
    wrapper.last("ORDER BY FIELD(status, 'pending', 'approved', 'rejected') ASC, create_time DESC");

    IPage<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

    // 填充用户信息
    Set<Long> userIds = page.getRecords().stream()
        .map(Comment::getUserId).collect(Collectors.toSet());
    Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>()
        : new HashMap<>(userMapper.selectByIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u)));

    // 填充文章标题
    Set<Long> postIds = page.getRecords().stream()
        .map(Comment::getPostId).collect(Collectors.toSet());
    Map<Long, String> postTitleMap = postIds.isEmpty() ? Map.of()
        : postMapper.selectByIds(postIds).stream()
            .collect(Collectors.toMap(Post::getId, p -> p.getTitle() != null ? p.getTitle() : "无标题"));

    // 填充回复对象昵称
    Set<Long> parentIds = page.getRecords().stream()
        .map(Comment::getParentId)
        .filter(pid -> pid != null && pid > 0)
        .collect(Collectors.toSet());
    Map<Long, Comment> parentCommentMap = parentIds.isEmpty() ? Map.of()
        : commentMapper.selectByIds(parentIds).stream()
            .collect(Collectors.toMap(Comment::getId, c -> c));
    Set<Long> parentUserIds = parentCommentMap.values().stream()
        .map(Comment::getUserId)
        .filter(uid -> !userMap.containsKey(uid))
        .collect(Collectors.toSet());
    if (!parentUserIds.isEmpty()) {
      userMapper.selectByIds(parentUserIds).forEach(u -> userMap.put(u.getId(), u));
    }

    List<CommentVO> records = page.getRecords().stream().map(c -> {
      CommentVO vo = new CommentVO();
      vo.setId(c.getId());
      vo.setPostId(c.getPostId());
      vo.setUserId(c.getUserId());
      User user = userMap.get(c.getUserId());
      vo.setNickname(user != null ? user.getNickname() : "未知");
      vo.setAvatar(user != null ? user.getAvatar() : null);
      vo.setParentId(c.getParentId());
      vo.setContent(c.getContent());
      vo.setLikeCount(c.getLikeCount());
      vo.setIsTop(c.getIsTop());
      vo.setStatus(c.getStatus());
      vo.setCreateTime(c.getCreateTime());
      vo.setPostTitle(postTitleMap.getOrDefault(c.getPostId(), "未知文章"));
      if (c.getParentId() != null && c.getParentId() > 0) {
        Comment parentComment = parentCommentMap.get(c.getParentId());
        if (parentComment != null) {
          User parentUser = userMap.get(parentComment.getUserId());
          vo.setReplyToNickname(parentUser != null ? parentUser.getNickname() : "未知");
        }
      }
      return vo;
    }).collect(Collectors.toList());

    return Map.of(
        "records", records,
        "total", page.getTotal(),
        "current", page.getCurrent(),
        "pages", page.getPages());
  }

  /**
   * 审核评论（更新状态）
   */
  public void updateStatus(Long commentId, String status) {
    if (!VALID_COMMENT_STATUSES.contains(status)) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, spam");
    }
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) {
      return;
    }
    String oldStatus = comment.getStatus();
    commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
        .eq(Comment::getId, commentId)
        .set(Comment::getStatus, status));
    updateCommentCount(comment.getPostId(), oldStatus, status);
  }

  /**
   * 置顶/取消置顶评论
   */
  public void toggleTop(Long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment != null) {
      commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
          .eq(Comment::getId, commentId)
          .set(Comment::getIsTop, !Boolean.TRUE.equals(comment.getIsTop())));
    }
  }

  /**
   * 删除评论
   */
  public void deleteComment(Long commentId) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment != null) {
      commentMapper.deleteById(commentId);
      if ("approved".equals(comment.getStatus())) {
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + comment.getPostId());
        redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, comment.getPostId().toString());
      }
    }
  }

  /**
   * 批量删除评论
   */
  public void batchDelete(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
    }
    List<Comment> comments = commentMapper.selectByIds(ids);
    commentMapper.deleteByIds(ids);
    for (Comment c : comments) {
      if ("approved".equals(c.getStatus())) {
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + c.getPostId());
        redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, c.getPostId().toString());
      }
    }
  }

  /**
   * 批量审核评论
   */
  public void batchUpdateStatus(List<Long> ids, String status) {
    if (!VALID_COMMENT_STATUSES.contains(status)) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "无效的状态值，仅支持: pending, approved, rejected, spam");
    }
    if (ids == null || ids.isEmpty()) {
      return;
    }
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "批量操作数量不能超过 " + MAX_BATCH_SIZE);
    }
    List<Comment> comments = commentMapper.selectByIds(ids);
    commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
        .in(Comment::getId, ids)
        .set(Comment::getStatus, status));
    for (Comment c : comments) {
      updateCommentCount(c.getPostId(), c.getStatus(), status);
    }
  }

  /**
   * 评论状态变更时更新 Redis 计数
   * 从非 approved → approved：计数+1
   * 从 approved → 非 approved：计数-1
   */
  private void updateCommentCount(Long postId, String oldStatus, String newStatus) {
    boolean wasApproved = "approved".equals(oldStatus);
    boolean isApproved = "approved".equals(newStatus);
    if (!wasApproved && isApproved) {
      redisTemplate.opsForValue().increment(KEY_COMMENT_COUNT + postId);
      redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, postId.toString());
    } else if (wasApproved && !isApproved) {
      RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_COUNT + postId);
      redisTemplate.opsForSet().add(KEY_COMMENT_DIRTY, postId.toString());
    }
  }
}
