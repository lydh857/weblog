package com.blog.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.entity.UserLike;
import com.blog.interaction.mapper.UserLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    /** Redis key: 用户对评论的点赞集合 */
    private static final String KEY_USER_LIKED_COMMENTS = "user:liked:comment:";
    /** Redis key: 有评论点赞变更的评论集合 */
    private static final String KEY_COMMENT_LIKE_DIRTY = "comment:like:dirty";
    /** Redis key: 用户对目标互动写入冷却 */
    private static final String KEY_STATE_GUARD = "interaction:state:guard:";
    /** 同一用户对同一目标的最小写入间隔 */
    private static final Duration STATE_GUARD_WINDOW = Duration.ofMillis(500);

    /**
     * 评论点赞/取消点赞（toggle）
     * @return true=点赞成功, false=取消点赞
     */
    public boolean toggleCommentLike(Long userId, Long commentId) {
        String userKey = KEY_USER_LIKED_COMMENTS + userId;
        String commentIdStr = commentId.toString();
        boolean currentLiked = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userKey, commentIdStr));
        return setCommentLikeState(userId, commentId, !currentLiked);
    }

    /**
     * 设置评论点赞状态（幂等）
     * @return true=点赞, false=取消点赞
     */
    public boolean setCommentLikeState(Long userId, Long commentId, boolean shouldLike) {
        guardStateWrite("comment-like", userId, commentId);

        String userKey = KEY_USER_LIKED_COMMENTS + userId;
        String commentIdStr = commentId.toString();
        boolean currentLiked = Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userKey, commentIdStr));
        if (currentLiked == shouldLike) {
            return currentLiked;
        }

        ensureCommentLikeCounterLoaded(commentId);
        if (shouldLike) {
            redisTemplate.opsForSet().add(userKey, commentIdStr);
            redisTemplate.opsForValue().increment(KEY_COMMENT_LIKE + commentId);
            redisTemplate.opsForSet().add(KEY_COMMENT_LIKE_DIRTY, commentIdStr);
            log.debug("评论点赞: userId={}, commentId={}", userId, commentId);
            return true;
        }

        redisTemplate.opsForSet().remove(userKey, commentIdStr);
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_COMMENT_LIKE + commentId);
        redisTemplate.opsForSet().add(KEY_COMMENT_LIKE_DIRTY, commentIdStr);
        log.debug("取消评论点赞: userId={}, commentId={}", userId, commentId);
        return false;
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
        String userKey = KEY_USER_LIKED_COMMENTS + userId;
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
        boolean currentLiked = isLiked(userId, postId);
        return setLikeState(userId, postId, !currentLiked);
    }

    /**
     * 设置文章点赞状态（幂等）
     * @return true=点赞, false=取消点赞
     */
    public boolean setLikeState(Long userId, Long postId, boolean shouldLike) {
        guardStateWrite("post-like", userId, postId);

        String userKey = KEY_USER_LIKED_POSTS + userId;
        String postIdStr = postId.toString();
        boolean currentLiked = resolvePostLikeState(userId, postId, userKey, postIdStr);
        if (currentLiked == shouldLike) {
            return currentLiked;
        }

        ensurePostLikeCounterLoaded(postId);
        if (shouldLike) {
            redisTemplate.opsForSet().add(userKey, postIdStr);
            redisTemplate.opsForValue().increment(KEY_POST_LIKE + postId);
            redisTemplate.opsForSet().add(KEY_LIKE_DIRTY, postIdStr);

            // MySQL 原子 upsert，避免并发下先查后写
            userLikeMapper.upsertActive(userId, "post", postId);

            log.debug("点赞: userId={}, postId={}", userId, postId);
            return true;
        }

        redisTemplate.opsForSet().remove(userKey, postIdStr);
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_POST_LIKE + postId);
        redisTemplate.opsForSet().add(KEY_LIKE_DIRTY, postIdStr);

        // MySQL 软删除（按唯一键）
        userLikeMapper.softDeleteByUnique(userId, "post", postId);

        log.debug("取消点赞: userId={}, postId={}", userId, postId);
        return false;
    }

    /**
     * 查询用户是否点赞了某篇文章
     */
    public boolean isLiked(Long userId, Long postId) {
        String userKey = KEY_USER_LIKED_POSTS + userId;
        return resolvePostLikeState(userId, postId, userKey, postId.toString());
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

    private void guardStateWrite(String action, Long userId, Long targetId) {
        String key = KEY_STATE_GUARD + action + ":" + userId + ":" + targetId;
        Boolean granted = redisTemplate.opsForValue().setIfAbsent(key, "1", STATE_GUARD_WINDOW);
        if (!Boolean.TRUE.equals(granted)) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "操作过于频繁，请稍后再试");
        }
    }

    private void ensurePostLikeCounterLoaded(Long postId) {
        if (redisTemplate.opsForValue().get(KEY_POST_LIKE + postId) == null) {
            getLikeCount(postId);
        }
    }

    private void ensureCommentLikeCounterLoaded(Long commentId) {
        if (redisTemplate.opsForValue().get(KEY_COMMENT_LIKE + commentId) == null) {
            getCommentLikeCount(commentId);
        }
    }

    private boolean resolvePostLikeState(Long userId, Long postId, String userKey, String postIdStr) {
        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, postIdStr);
        if (Boolean.TRUE.equals(isMember)) {
            return true;
        }
        if (Boolean.FALSE.equals(isMember) && Boolean.TRUE.equals(redisTemplate.hasKey(userKey))) {
            return false;
        }

        boolean likedInDb = queryPostLikeStateFromDb(userId, postId);
        if (likedInDb) {
            redisTemplate.opsForSet().add(userKey, postIdStr);
        }
        return likedInDb;
    }

    private boolean queryPostLikeStateFromDb(Long userId, Long postId) {
        Long count = userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getTargetType, "post")
                .eq(UserLike::getTargetId, postId));
        return count != null && count > 0;
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
