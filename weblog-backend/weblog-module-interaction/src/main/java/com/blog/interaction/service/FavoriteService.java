package com.blog.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.redis.RedisCounterUtil;
import com.blog.interaction.entity.UserFavorite;
import com.blog.interaction.mapper.UserFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收藏服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final StringRedisTemplate redisTemplate;
    private final UserFavoriteMapper userFavoriteMapper;
    private final JdbcTemplate jdbcTemplate;

    /** Redis key: 文章收藏数 */
    private static final String KEY_POST_COLLECT = "post:collect:";
    /** Redis key: 用户收藏文章集合 */
    private static final String KEY_USER_FAVORITES = "user:fav:";
    /** Redis key: 有收藏变更的文章集合 */
    private static final String KEY_COLLECT_DIRTY = "post:collect:dirty";
    /** Redis key: 用户对目标互动写入冷却 */
    private static final String KEY_STATE_GUARD = "interaction:state:guard:";
    /** 同一用户对同一目标的最小写入间隔 */
    private static final Duration STATE_GUARD_WINDOW = Duration.ofMillis(500);

    /**
     * 收藏/取消收藏（toggle）
     * @return true=收藏成功, false=取消收藏
     */
    public boolean toggleFavorite(Long userId, Long postId) {
        boolean currentFavorited = isFavorited(userId, postId);
        return setFavoriteState(userId, postId, !currentFavorited);
    }

    /**
     * 设置收藏状态（幂等）
     * @return true=收藏, false=取消收藏
     */
    public boolean setFavoriteState(Long userId, Long postId, boolean shouldFavorite) {
        guardStateWrite("post-favorite", userId, postId);

        String userKey = KEY_USER_FAVORITES + userId;
        String postIdStr = postId.toString();
        boolean currentFavorited = resolveFavoriteState(userId, postId, userKey, postIdStr);
        if (currentFavorited == shouldFavorite) {
            return currentFavorited;
        }

        ensureCollectCounterLoaded(postId);
        if (shouldFavorite) {
            redisTemplate.opsForSet().add(userKey, postIdStr);
            redisTemplate.opsForValue().increment(KEY_POST_COLLECT + postId);
            redisTemplate.opsForSet().add(KEY_COLLECT_DIRTY, postIdStr);

            userFavoriteMapper.upsertActive(userId, postId);

            log.debug("收藏: userId={}, postId={}", userId, postId);
            return true;
        }

        redisTemplate.opsForSet().remove(userKey, postIdStr);
        RedisCounterUtil.safeDecrement(redisTemplate, KEY_POST_COLLECT + postId);
        redisTemplate.opsForSet().add(KEY_COLLECT_DIRTY, postIdStr);

        userFavoriteMapper.softDeleteByUnique(userId, postId);

        log.debug("取消收藏: userId={}, postId={}", userId, postId);
        return false;
    }

    /**
     * 查询用户是否收藏了某篇文章
     */
    public boolean isFavorited(Long userId, Long postId) {
        String userKey = KEY_USER_FAVORITES + userId;
        return resolveFavoriteState(userId, postId, userKey, postId.toString());
    }

    /**
     * 批量查询用户对多篇文章的收藏状态
     */
    public Set<Long> getFavoritedPostIds(Long userId, List<Long> postIds) {
        String userKey = KEY_USER_FAVORITES + userId;
        Set<String> members = redisTemplate.opsForSet().members(userKey);
        if (members != null && !members.isEmpty()) {
            return members.stream()
                    .map(Long::parseLong)
                    .filter(postIds::contains)
                    .collect(Collectors.toSet());
        }
        List<UserFavorite> favs = userFavoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .in(UserFavorite::getPostId, postIds));
        return favs.stream().map(UserFavorite::getPostId).collect(Collectors.toSet());
    }

    /**
     * 获取用户收藏的文章ID列表（分页）
     */
    public IPage<UserFavorite> getUserFavorites(Long userId, int pageNum, int pageSize) {
        return userFavoriteMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreateTime));
    }

    /**
     * 获取有变更的文章收藏数（供定时任务落库）
     */
    public Map<Long, Long> getDirtyCollectCounts() {
        Set<String> dirtyIds = redisTemplate.opsForSet().members(KEY_COLLECT_DIRTY);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (String idStr : dirtyIds) {
            Long postId = Long.parseLong(idStr);
            String val = redisTemplate.opsForValue().get(KEY_POST_COLLECT + postId);
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
        redisTemplate.opsForSet().remove(KEY_COLLECT_DIRTY, postId.toString());
    }

    /**
     * 获取文章收藏数（Redis 优先，无值时从数据库初始化）
     */
    public long getCollectCount(Long postId) {
        String val = redisTemplate.opsForValue().get(KEY_POST_COLLECT + postId);
        if (val != null) return Long.parseLong(val);
        try {
            Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT IFNULL(collect_count, 0) FROM t_post WHERE id = ?", Integer.class, postId);
            long count = dbCount != null ? dbCount : 0;
            redisTemplate.opsForValue().set(KEY_POST_COLLECT + postId, String.valueOf(count));
            return count;
        } catch (Exception e) {
            log.warn("回填收藏数失败: postId={}, {}", postId, e.getMessage());
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

    private void ensureCollectCounterLoaded(Long postId) {
        if (redisTemplate.opsForValue().get(KEY_POST_COLLECT + postId) == null) {
            getCollectCount(postId);
        }
    }

    private boolean resolveFavoriteState(Long userId, Long postId, String userKey, String postIdStr) {
        Boolean isMember = redisTemplate.opsForSet().isMember(userKey, postIdStr);
        if (Boolean.TRUE.equals(isMember)) {
            return true;
        }
        if (Boolean.FALSE.equals(isMember) && Boolean.TRUE.equals(redisTemplate.hasKey(userKey))) {
            return false;
        }

        Long count = userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getPostId, postId));
        boolean favoritedInDb = count != null && count > 0;
        if (favoritedInDb) {
            redisTemplate.opsForSet().add(userKey, postIdStr);
        }
        return favoritedInDb;
    }
}
