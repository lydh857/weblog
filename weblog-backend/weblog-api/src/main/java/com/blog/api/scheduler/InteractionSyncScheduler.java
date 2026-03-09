package com.blog.api.scheduler;

import com.blog.interaction.service.CommentService;
import com.blog.interaction.service.FavoriteService;
import com.blog.interaction.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    /**
     * 每小时第15分钟执行（与阅读数同步错开）
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 15 * * * ?")
    public synchronized void syncInteractionCounts() {
        syncLikeCounts();
        syncCollectCounts();
        syncCommentCounts();
        syncCommentLikeCounts();
    }

    private void syncLikeCounts() {
        Map<Long, Long> dirty = likeService.getDirtyLikeCounts();
        if (dirty.isEmpty()) return;

        log.info("同步点赞数，共 {} 篇文章", dirty.size());
        for (Map.Entry<Long, Long> entry : dirty.entrySet()) {
            try {
                jdbcTemplate.update(
                    "UPDATE t_post SET like_count = GREATEST(0, ?) WHERE id = ?",
                    entry.getValue().intValue(), entry.getKey());
                likeService.clearDirty(entry.getKey());
            } catch (Exception e) {
                log.error("同步文章 {} 点赞数失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void syncCollectCounts() {
        Map<Long, Long> dirty = favoriteService.getDirtyCollectCounts();
        if (dirty.isEmpty()) return;

        log.info("同步收藏数，共 {} 篇文章", dirty.size());
        for (Map.Entry<Long, Long> entry : dirty.entrySet()) {
            try {
                jdbcTemplate.update(
                    "UPDATE t_post SET collect_count = GREATEST(0, ?) WHERE id = ?",
                    entry.getValue().intValue(), entry.getKey());
                favoriteService.clearDirty(entry.getKey());
            } catch (Exception e) {
                log.error("同步文章 {} 收藏数失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void syncCommentCounts() {
        Map<Long, Long> dirty = commentService.getDirtyCommentCounts();
        if (dirty.isEmpty()) return;

        log.info("同步评论数，共 {} 篇文章", dirty.size());
        for (Map.Entry<Long, Long> entry : dirty.entrySet()) {
            try {
                jdbcTemplate.update(
                    "UPDATE t_post SET comment_count = GREATEST(0, ?) WHERE id = ?",
                    entry.getValue().intValue(), entry.getKey());
                commentService.clearDirty(entry.getKey());
            } catch (Exception e) {
                log.error("同步文章 {} 评论数失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void syncCommentLikeCounts() {
        Map<Long, Long> dirty = likeService.getDirtyCommentLikeCounts();
        if (dirty.isEmpty()) return;

        log.info("同步评论点赞数，共 {} 条评论", dirty.size());
        for (Map.Entry<Long, Long> entry : dirty.entrySet()) {
            try {
                jdbcTemplate.update(
                    "UPDATE t_comment SET like_count = GREATEST(0, ?) WHERE id = ?",
                    entry.getValue().intValue(), entry.getKey());
                likeService.clearCommentLikeDirty(entry.getKey());
            } catch (Exception e) {
                log.error("同步评论 {} 点赞数失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
