package com.blog.api.scheduler;

import com.blog.interaction.entity.PostReadDaily;
import com.blog.interaction.mapper.PostReadDailyMapper;
import com.blog.interaction.service.ReadCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * 阅读数落库定时任务
 * 每小时将 Redis 中的阅读增量同步到 MySQL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncScheduler {

    private final ReadCountService readCountService;
    private final PostReadDailyMapper postReadDailyMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 每小时执行一次：Redis 阅读增量 → t_post.view_count + t_post_read_daily
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 0 * * * ?")
    public synchronized void syncViewCounts() {
        Map<Long, Long> dirtyCounts = readCountService.getDirtyPostCounts();
        if (dirtyCounts.isEmpty()) {
            return;
        }

        log.info("开始同步阅读数，共 {} 篇文章有增量", dirtyCounts.size());
        LocalDate today = LocalDate.now();
        int synced = 0;

        for (Map.Entry<Long, Long> entry : dirtyCounts.entrySet()) {
            Long postId = entry.getKey();
            long increment = entry.getValue();
            if (increment <= 0) continue;

            try {
                // 1. 直接 SQL 更新 t_post.view_count，避免 N+1
                jdbcTemplate.update(
                    "UPDATE t_post SET view_count = view_count + ? WHERE id = ?",
                    (int) increment, postId);

                // 2. 更新 t_post_read_daily（upsert 模式）
                int updated = jdbcTemplate.update(
                    "UPDATE t_post_read_daily SET read_count = read_count + ? WHERE post_id = ? AND read_date = ?",
                    (int) increment, postId, today);
                if (updated == 0) {
                    PostReadDaily daily = new PostReadDaily();
                    daily.setPostId(postId);
                    daily.setReadDate(today);
                    daily.setReadCount((int) increment);
                    postReadDailyMapper.insert(daily);
                }

                // 3. 清除 Redis 增量
                readCountService.clearDirtyCount(postId, increment);
                synced++;
            } catch (Exception e) {
                log.error("同步文章 {} 阅读数失败: {}", postId, e.getMessage());
            }
        }

        log.info("阅读数同步完成，成功 {} 篇", synced);
    }
}
