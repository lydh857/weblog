package com.blog.api.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.blog.infra.redis.RedisService;
import com.blog.system.service.SystemConfigService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 排行榜计算定时任务
 * 每小时计算一次排行榜数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingComputeScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisService redisService;
    private final SystemConfigService systemConfigService;
    private static final String LOCK_KEY = "scheduler:ranking:compute";
    private static final String LAST_RUN_TS_KEY = "scheduler:ranking:last-run-ts";
    private static final String INTERVAL_CONFIG_KEY = "ranking_update_interval";
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int MIN_INTERVAL_MINUTES = 5;
    private static final int MAX_INTERVAL_MINUTES = 1440;

    /**
     * 每分钟执行一次，由系统配置控制真实计算间隔
     * 注：synchronized 仅对单机部署有效，多实例部署需改为 Redis 分布式锁
     */
    @Scheduled(cron = "0 * * * * ?")
    public void computeRankings() {
        computeRankingsInternal(false);
    }

    public void computeRankingsNow() {
        computeRankingsInternal(true);
    }

    private void computeRankingsInternal(boolean force) {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("排行榜任务跳过：未获取到分布式锁");
            return;
        }

        try {
            long now = System.currentTimeMillis();
            int intervalMinutes = resolveIntervalMinutes();
            if (!force && !shouldRun(now, intervalMinutes)) {
                return;
            }

            log.info("开始计算排行榜... intervalMinutes={}", intervalMinutes);
            LocalDate today = LocalDate.now();
            // 总榜
            computeRank(4, null, null, null);
            // 日榜
            computeRank(1, today, today, today);
            // 周榜
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            computeRank(2, weekStart, today, weekStart);
            // 月榜
            LocalDate monthStart = today.withDayOfMonth(1);
            computeRank(3, monthStart, today, monthStart);
            redisService.set(LAST_RUN_TS_KEY, String.valueOf(now));
            log.info("排行榜计算完成");
        } catch (Exception e) {
            log.error("排行榜计算失败: {}", e.getMessage(), e);
        } finally {
            releaseLock(lockValue);
        }
    }

    private void computeRank(int rankType, LocalDate dateFrom, LocalDate dateTo, LocalDate statDate) {
        List<Map<String, Object>> rows;
        if (rankType == 4) {
            // 总榜：直接用 t_post 的累计数据
            rows = jdbcTemplate.queryForList("""
                SELECT p.id AS post_id, p.category_id,
                       (p.view_count + p.like_count * 2 + p.collect_count * 3) AS score
                FROM t_post p
                WHERE p.is_deleted = 0 AND p.status = 'published'
                ORDER BY score DESC
                LIMIT 100
                """);
        } else {
            // 日/周/月榜：用 t_post_read_daily 的区间数据 + 基础互动数据
            rows = jdbcTemplate.queryForList("""
                SELECT p.id AS post_id, p.category_id,
                       (COALESCE(SUM(d.read_count), 0) + p.like_count * 2 + p.collect_count * 3) AS score
                FROM t_post p
                LEFT JOIN t_post_read_daily d ON p.id = d.post_id AND d.read_date BETWEEN ? AND ?
                WHERE p.is_deleted = 0 AND p.status = 'published'
                GROUP BY p.id
                HAVING score > 0
                ORDER BY score DESC
                LIMIT 100
                """, dateFrom, dateTo);
        }

        // 清除旧排行数据
        if (rankType == 4) {
            jdbcTemplate.update("DELETE FROM t_post_ranking WHERE rank_type = 4 AND stat_date IS NULL AND category_id IS NULL");
        } else {
            jdbcTemplate.update("DELETE FROM t_post_ranking WHERE rank_type = ? AND stat_date = ? AND category_id IS NULL",
                rankType, statDate);
        }

        if (rows.isEmpty()) {
            return;
        }

        // 插入新排行（批量）
        final int[] rankNum = {1};
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO t_post_ranking (post_id, category_id, rank_type, rank_num, score, stat_date, update_time)
                VALUES (?, NULL, ?, ?, ?, ?, NOW())
                """,
                rows,
                rows.size(),
                (ps, row) -> {
                    Number postId = (Number) row.get("post_id");
                    Number score = (Number) row.get("score");
                    ps.setLong(1, postId.longValue());
                    ps.setInt(2, rankType);
                    ps.setInt(3, rankNum[0]++);
                    ps.setInt(4, score.intValue());
                    ps.setObject(5, statDate);
                }
        );
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(10));
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        redisService.releaseLock(LOCK_KEY, lockValue);
    }

    private int resolveIntervalMinutes() {
        int configured = systemConfigService.getIntValue(INTERVAL_CONFIG_KEY, DEFAULT_INTERVAL_MINUTES);
        if (configured < MIN_INTERVAL_MINUTES || configured > MAX_INTERVAL_MINUTES) {
            return DEFAULT_INTERVAL_MINUTES;
        }
        return configured;
    }

    private boolean shouldRun(long now, int intervalMinutes) {
        String lastRunRaw = redisService.get(LAST_RUN_TS_KEY);
        if (lastRunRaw == null || lastRunRaw.isBlank()) {
            return true;
        }
        try {
            long lastRun = Long.parseLong(lastRunRaw);
            long minIntervalMillis = Duration.ofMinutes(intervalMinutes).toMillis();
            return now - lastRun >= minIntervalMillis;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }
}
