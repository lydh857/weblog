package com.blog.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.entity.UserLike;
import com.blog.interaction.mapper.UserLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 点赞服务
 * - Redis 实时计数 + 用户点赞状态
 * - MySQL 持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final StringRedisTemplate redisTemplate;
    private final UserLikeMapper userLikeMapper;
    private final JdbcTemplate jdbcTemplate;

    /** Redis key: 文章点赞数 */
    private static final String KEY_POST_LIKE = "post:like:";
    /** Redis key: 用户对文章的点赞集合 */
    private static final String KEY_USER_LIKED_POSTS = "user:liked:post:";
    /** Redis key: 有点赞变更的文章集合 */
    private static final String KEY_LIKE_DIRTY = "post:like:dirty";

    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    /** Redis key: 评论点赞数 */
    private static final String KEY_COMMENT_LIKE = "comment:like:";
    /** Redis key: 有评论点赞变更的评论集合 */
    private static final String KEY_COMMENT_LIKE_DIRTY = "comment:like:dirty";

    /**
     * 评论点赞/取消点赞（toggle）
     * @return true=点赞成功, false=取消点赞
     */
    public boolean toggleCommentLike(Long userId, Long commentId) {
        String userKey = "user:liked:comment:" + userId;
        String commentIdStr = commentId.toString();

        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, commentIdStr);
        if (Boolean.TRUE.equals(isMember)) {
            redisTemplate.opsForSet().remove(userKey, commentIdStr);
            RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_LIKE + commentId);
            redisTemplate.opsForSet().add(KEY_COMMENT_LIKE_DIRTY, commentIdStr);
            log.debug("取消评论点赞: userId={}, commentId={}", userId, commentId);
            return false;
        } else {
            redisTemplate.opsForSet().add(userKey, commentIdStr);
            redisTemplate.opsForValue().increment(KEY_COMMENT_LIKE + commentId);
            redisTemplate.opsForSet().add(KEY_COMMENT_LIKE_DIRTY, commentIdStr);
            log.debug("评论点赞: userId={}, commentId={}", userId, commentId);
            return true;
        }
    }

    /**
     * 获取评论点赞数（Redis 优先，无值时从数据库初始化）
     */
    public long getCommentLikeCount(Long commentId) {
        String val = redisTemplate.opsForValue().get(KEY_COMMENT_LIKE + commentId);
        if (val != null) return Long.parseLong(val);
        try {
            Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(like_count, 0) FROM t_comment WHERE id = ?", Integer.class, commentId);
            long count = dbCount != null ? dbCount : 0;
            redisTemplate.opsForValue().set(KEY_COMMENT_LIKE + commentId, String.valueOf(count));
            return count;
        } catch (Exception e) {
            log.warn("回填评论点赞数失败: commentId={}, {}", commentId, e.getMessage());
            return 0;
        }
    }

    /**
     * 批量查询用户对多条评论的点赞状态
     */
    public Set<Long> getCommentLikedIds(Long userId, List<Long> commentIds) {
        if (commentIds.isEmpty()) return Set.of();
        String userKey = "user:liked:comment:" + userId;
        Set<String> members = redisTemplate.opsForSet().members(userKey);
        if (members == null || members.isEmpty()) return Set.of();
        Set<Long> likedSet = members.stream().map(Long::parseLong).collect(Collectors.toSet());
        return commentIds.stream().filter(likedSet::contains).collect(Collectors.toSet());
    }

    /**
     * 获取有变更的评论点赞数（供定时任务落库）
     */
    public Map<Long, Long> getDirtyCommentLikeCounts() {
        Set<String> dirtyIds = redisTemplate.opsForSet().members(KEY_COMMENT_LIKE_DIRTY);
        if (dirtyIds == null || dirtyIds.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        for (String idStr : dirtyIds) {
            Long commentId = Long.parseLong(idStr);
            String val = redisTemplate.opsForValue().get(KEY_COMMENT_LIKE + commentId);
            if (val != null) result.put(commentId, Long.parseLong(val));
        }
        return result;
    }

    /**
     * 清除评论点赞脏标记
     */
    public void clearCommentLikeDirty(Long commentId) {
        redisTemplate.opsForSet().remove(KEY_COMMENT_LIKE_DIRTY, commentId.toString());
    }

    /**
     * 点赞/取消点赞（toggle）
     * @return true=点赞成功, false=取消点赞
     */
    public boolean toggleLike(Long userId, Long postId) {
        String userKey = KEY_USER_LIKED_POSTS + userId;
        String postIdStr = postId.toString();

        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, postIdStr);
        if (Boolean.TRUE.equals(isMember)) {
            // 取消点赞
            redisTemplate.opsForSet().remove(userKey, postIdStr);
            RedisCounterUtil.safeDecrement(redisTemplate, KEY_POST_LIKE + postId);
            redisTemplate.opsForSet().add(KEY_LIKE_DIRTY, postIdStr);

            // MySQL 软删除
            userLikeMapper.delete(new LambdaQueryWrapper<UserLike>()
                    .eq(UserLike::getUserId, userId)
                    .eq(UserLike::getTargetType, "post")
                    .eq(UserLike::getTargetId, postId));

            log.debug("取消点赞: userId={}, postId={}", userId, postId);
            return false;
        } else {
            // 点赞
            redisTemplate.opsForSet().add(userKey, postIdStr);
            redisTemplate.opsForValue().increment(KEY_POST_LIKE + postId);
            redisTemplate.opsForSet().add(KEY_LIKE_DIRTY, postIdStr);

            // MySQL 插入（先查是否有记录，包括软删除的）
            UserLike existing = userLikeMapper.selectIncludeDeleted(userId, "post", postId);
            if (existing != null) {
                // 恢复软删除记录
                userLikeMapper.restoreById(existing.getId());
            } else {
                UserLike like = new UserLike();
                like.setUserId(userId);
                like.setTargetType("post");
                like.setTargetId(postId);
                userLikeMapper.insert(like);
            }

            log.debug("点赞: userId={}, postId={}", userId, postId);
            return true;
        }
    }

    /**
     * 查询用户是否点赞了某篇文章
     */
    public boolean isLiked(Long userId, Long postId) {
        String userKey = KEY_USER_LIKED_POSTS + userId;
        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, postId.toString());
        if (isMember != null) {
            return isMember;
        }
        // Redis 无数据时回查 MySQL
        Long count = userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getTargetType, "post")
                .eq(UserLike::getTargetId, postId));
        return count > 0;
    }

    /**
     * 批量查询用户对多篇文章的点赞状态
     */
    public Set<Long> getLikedPostIds(Long userId, List<Long> postIds) {
        String userKey = KEY_USER_LIKED_POSTS + userId;
        Set<String> members = redisTemplate.opsForSet().members(userKey);
        if (members != null && !members.isEmpty()) {
            return members.stream()
                    .map(Long::parseLong)
                    .filter(postIds::contains)
                    .collect(Collectors.toSet());
        }
        // 回查 MySQL
        List<UserLike> likes = userLikeMapper.selectList(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getTargetType, "post")
                .in(UserLike::getTargetId, postIds));
        return likes.stream().map(UserLike::getTargetId).collect(Collectors.toSet());
    }

    /**
     * 获取文章点赞数（Redis 优先，无值时从数据库初始化）
     */
    public long getLikeCount(Long postId) {
        String val = redisTemplate.opsForValue().get(KEY_POST_LIKE + postId);
        if (val != null) return Long.parseLong(val);
        // Redis 无值，从数据库回填
        try {
            Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(like_count, 0) FROM t_post WHERE id = ?", Integer.class, postId);
            long count = dbCount != null ? dbCount : 0;
            redisTemplate.opsForValue().set(KEY_POST_LIKE + postId, String.valueOf(count));
            return count;
        } catch (Exception e) {
            log.warn("回填点赞数失败: postId={}, {}", postId, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取有变更的文章点赞数（供定时任务落库）
     */
    public Map<Long, Long> getDirtyLikeCounts() {
        Set<String> dirtyIds = redisTemplate.opsForSet().members(KEY_LIKE_DIRTY);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (String idStr : dirtyIds) {
            Long postId = Long.parseLong(idStr);
            String val = redisTemplate.opsForValue().get(KEY_POST_LIKE + postId);
            if (val != null) {
                result.put(postId, Long.parseLong(val));
            }
        }
        return result;
    }

    /**
     * 清除脏标记
     */
    public void clearDirty(Long postId) {
        redisTemplate.opsForSet().remove(KEY_LIKE_DIRTY, postId.toString());
    }
}
