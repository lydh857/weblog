package com.blog.api.scheduler;

import com.blog.interaction.entity.PostReadDaily;
import com.blog.interaction.mapper.PostReadDailyMapper;
import com.blog.interaction.service.ReadCountService;
import com.blog.infra.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final StringRedisTemplate redisTemplate;
    private final RedisService redisService;
    private static final String LOCK_KEY = "scheduler:view-count:sync";

    /**
     * 每小时执行一次：Redis 阅读增量 → t_post.view_count + t_post_read_daily
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncViewCounts() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("阅读数同步任务跳过：未获取到分布式锁");
            return;
        }

        try {
            doSyncViewCounts();
        } finally {
            releaseLock(lockValue);
        }
    }

    private void doSyncViewCounts() {
        Map<Long, Long> dirtyCounts = readCountService.getDirtyPostCounts();
        if (dirtyCounts.isEmpty()) {
            return;
        }

        log.info("开始同步阅读数，共 {} 篇文章有增量", dirtyCounts.size());
        LocalDate today = LocalDate.now();

        List<Map.Entry<Long, Long>> entries = dirtyCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .toList();
        if (entries.isEmpty()) {
            return;
        }

        try {
            jdbcTemplate.batchUpdate(
                    "UPDATE t_post SET view_count = view_count + ? WHERE id = ?",
                    entries,
                    entries.size(),
                    (ps, entry) -> {
                        ps.setInt(1, entry.getValue().intValue());
                        ps.setLong(2, entry.getKey());
                    }
            );

            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO t_post_read_daily (post_id, read_date, read_count, create_time, update_time)
                    VALUES (?, ?, ?, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE
                      read_count = read_count + VALUES(read_count),
                      update_time = NOW()
                    """,
                    entries,
                    entries.size(),
                    (ps, entry) -> {
                        ps.setLong(1, entry.getKey());
                        ps.setObject(2, today);
                        ps.setInt(3, entry.getValue().intValue());
                    }
            );

            entries.forEach(entry -> readCountService.clearDirtyCount(entry.getKey(), entry.getValue()));
            log.info("阅读数同步完成，成功 {} 篇", entries.size());
        } catch (Exception e) {
            log.error("批量同步阅读数失败，回退单条同步: {}", e.getMessage());
            fallbackSync(entries, today);
        }
    }

    private void fallbackSync(List<Map.Entry<Long, Long>> entries, LocalDate today) {
        int synced = 0;
        for (Map.Entry<Long, Long> entry : entries) {
            Long postId = entry.getKey();
            long increment = entry.getValue();
            try {
                jdbcTemplate.update(
                        "UPDATE t_post SET view_count = view_count + ? WHERE id = ?",
                        (int) increment, postId);

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
                readCountService.clearDirtyCount(postId, increment);
                synced++;
            } catch (Exception ex) {
                log.error("回退同步文章 {} 阅读数失败: {}", postId, ex.getMessage());
            }
        }
        log.info("阅读数回退同步完成，成功 {} 篇", synced);
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(10));
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        redisService.releaseLock(LOCK_KEY, lockValue);
    }
}
