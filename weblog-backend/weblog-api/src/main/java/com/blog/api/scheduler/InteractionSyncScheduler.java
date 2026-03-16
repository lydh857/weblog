package com.blog.api.scheduler;

import com.blog.interaction.service.CommentService;
import com.blog.interaction.service.FavoriteService;
import com.blog.interaction.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 互动数落库定时任务
 * 每小时将 Redis 中的点赞/收藏/评论增量同步到 t_post
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionSyncScheduler {

    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final CommentService commentService;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private static final String LOCK_KEY = "scheduler:interaction:sync";

    /**
     * 每小时第15分钟执行（与阅读数同步错开）
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 15 * * * ?")
    public void syncInteractionCounts() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("互动同步任务跳过：未获取到分布式锁");
            return;
        }
        try {
            syncLikeCounts();
            syncCollectCounts();
            syncCommentCounts();
            syncCommentLikeCounts();
        } finally {
            releaseLock(lockValue);
        }
    }

    private void syncLikeCounts() {
        Map<Long, Long> dirty = likeService.getDirtyLikeCounts();
        if (dirty.isEmpty()) return;

        List<Map.Entry<Long, Long>> entries = dirty.entrySet().stream().toList();
        log.info("同步点赞数，共 {} 篇文章", entries.size());
        try {
            batchUpdate("UPDATE t_post SET like_count = GREATEST(0, ?) WHERE id = ?", entries);
            entries.forEach(entry -> likeService.clearDirty(entry.getKey()));
        } catch (Exception e) {
            log.error("批量同步点赞数失败，回退单条同步: {}", e.getMessage());
            fallbackSync(entries, "UPDATE t_post SET like_count = GREATEST(0, ?) WHERE id = ?", likeService::clearDirty);
        }
    }

    private void syncCollectCounts() {
        Map<Long, Long> dirty = favoriteService.getDirtyCollectCounts();
        if (dirty.isEmpty()) return;

        List<Map.Entry<Long, Long>> entries = dirty.entrySet().stream().toList();
        log.info("同步收藏数，共 {} 篇文章", entries.size());
        try {
            batchUpdate("UPDATE t_post SET collect_count = GREATEST(0, ?) WHERE id = ?", entries);
            entries.forEach(entry -> favoriteService.clearDirty(entry.getKey()));
        } catch (Exception e) {
            log.error("批量同步收藏数失败，回退单条同步: {}", e.getMessage());
            fallbackSync(entries, "UPDATE t_post SET collect_count = GREATEST(0, ?) WHERE id = ?", favoriteService::clearDirty);
        }
    }

    private void syncCommentCounts() {
        Map<Long, Long> dirty = commentService.getDirtyCommentCounts();
        if (dirty.isEmpty()) return;

        List<Map.Entry<Long, Long>> entries = dirty.entrySet().stream().toList();
        log.info("同步评论数，共 {} 篇文章", entries.size());
        try {
            batchUpdate("UPDATE t_post SET comment_count = GREATEST(0, ?) WHERE id = ?", entries);
            entries.forEach(entry -> commentService.clearDirty(entry.getKey()));
        } catch (Exception e) {
            log.error("批量同步评论数失败，回退单条同步: {}", e.getMessage());
            fallbackSync(entries, "UPDATE t_post SET comment_count = GREATEST(0, ?) WHERE id = ?", commentService::clearDirty);
        }
    }

    private void syncCommentLikeCounts() {
        Map<Long, Long> dirty = likeService.getDirtyCommentLikeCounts();
        if (dirty.isEmpty()) return;

        List<Map.Entry<Long, Long>> entries = dirty.entrySet().stream().toList();
        log.info("同步评论点赞数，共 {} 条评论", entries.size());
        try {
            batchUpdate("UPDATE t_comment SET like_count = GREATEST(0, ?) WHERE id = ?", entries);
            entries.forEach(entry -> likeService.clearCommentLikeDirty(entry.getKey()));
        } catch (Exception e) {
            log.error("批量同步评论点赞数失败，回退单条同步: {}", e.getMessage());
            fallbackSync(entries, "UPDATE t_comment SET like_count = GREATEST(0, ?) WHERE id = ?", likeService::clearCommentLikeDirty);
        }
    }

    private void batchUpdate(String sql, List<Map.Entry<Long, Long>> entries) {
        jdbcTemplate.batchUpdate(sql, entries, entries.size(), (ps, entry) -> {
            ps.setInt(1, entry.getValue().intValue());
            ps.setLong(2, entry.getKey());
        });
    }

    private void fallbackSync(List<Map.Entry<Long, Long>> entries,
                              String sql,
                              java.util.function.Consumer<Long> clearAction) {
        for (Map.Entry<Long, Long> entry : entries) {
            try {
                jdbcTemplate.update(sql, entry.getValue().intValue(), entry.getKey());
                clearAction.accept(entry.getKey());
            } catch (Exception ex) {
                log.error("回退单条同步失败: id={}, message={}", entry.getKey(), ex.getMessage());
            }
        }
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(10));
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        String current = redisTemplate.opsForValue().get(LOCK_KEY);
        if (lockValue.equals(current)) {
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
