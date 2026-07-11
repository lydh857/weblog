package com.blog.api.scheduler;

import com.blog.infra.redis.RedisService;
import com.blog.system.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据治理定时任务：保留策略、容量预警、弱约束巡检、字符集基线巡检。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blog.security.data-governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataGovernanceScheduler {

    private static final String LOCK_KEY = "scheduler:data-governance";

    private static final List<String> ORPHAN_CHECK_SQLS = List.of(
            "SELECT COUNT(*) FROM t_post_tag pt LEFT JOIN t_post p ON pt.post_id = p.id WHERE p.id IS NULL",
            "SELECT COUNT(*) FROM t_post_tag pt LEFT JOIN t_tag t ON pt.tag_id = t.id WHERE t.id IS NULL",
            "SELECT COUNT(*) FROM t_user_view_log uv LEFT JOIN t_post p ON uv.post_id = p.id WHERE uv.post_id IS NOT NULL AND p.id IS NULL",
            "SELECT COUNT(*) FROM t_post_ranking r LEFT JOIN t_post p ON r.post_id = p.id WHERE p.id IS NULL",
            "SELECT COUNT(*) FROM t_comment c LEFT JOIN t_post p ON c.post_id = p.id WHERE p.id IS NULL",
            "SELECT COUNT(*) FROM t_comment c LEFT JOIN t_user u ON c.user_id = u.id WHERE u.id IS NULL"
    );

    private static final List<String> ORPHAN_CHECK_NAMES = List.of(
            "t_post_tag.post_id -> t_post.id",
            "t_post_tag.tag_id -> t_tag.id",
            "t_user_view_log.post_id -> t_post.id",
            "t_post_ranking.post_id -> t_post.id",
            "t_comment.post_id -> t_post.id",
            "t_comment.user_id -> t_user.id"
    );

    private static final List<String> COLLATION_CHECK_TABLES = List.of(
            "t_login_log",
            "t_audit_log",
            "t_post",
            "t_user",
            "t_post_tag",
            "t_user_view_log",
            "t_post_ranking"
    );

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisService redisService;
    private final EmailService emailService;

    @Value("${blog.security.data-governance.user-view-log-retention-days:180}")
    private int userViewLogRetentionDays;

    @Value("${blog.security.data-governance.post-ranking-retention-days:180}")
    private int postRankingRetentionDays;

    @Value("${blog.security.data-governance.post-read-daily-retention-days:365}")
    private int postReadDailyRetentionDays;

    @Value("${blog.security.data-governance.capacity.user-view-log-warn-rows:5000000}")
    private long userViewLogWarnRows;

    @Value("${blog.security.data-governance.capacity.post-ranking-warn-rows:500000}")
    private long postRankingWarnRows;

    @Value("${blog.security.data-governance.capacity.login-log-warn-rows:3000000}")
    private long loginLogWarnRows;

    @Value("${blog.security.data-governance.capacity.audit-log-warn-rows:3000000}")
    private long auditLogWarnRows;

    @Value("${blog.security.data-governance.orphan-check-enabled:true}")
    private boolean orphanCheckEnabled;

    @Value("${blog.security.data-governance.collation-check-enabled:true}")
    private boolean collationCheckEnabled;

    @Value("${blog.security.data-governance.collation-baseline:utf8mb4_0900_ai_ci}")
    private String collationBaseline;

    @Value("${blog.security.data-governance.notify-on-anomaly:false}")
    private boolean notifyOnAnomaly;

    /**
     * 每天凌晨执行数据治理任务。
     */
    @Scheduled(cron = "${blog.security.data-governance.cron:0 40 4 * * ?}")
    public void runGovernanceTask() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("数据治理任务跳过：未获取到分布式锁");
            return;
        }

        long startNs = System.nanoTime();
        try {
            CleanupSummary cleanupSummary = cleanupOldData();
            List<String> anomalies = new ArrayList<>();
            anomalies.addAll(checkCapacity());

            if (orphanCheckEnabled) {
                anomalies.addAll(checkWeakConstraintOrphans());
            }

            if (collationCheckEnabled) {
                anomalies.addAll(checkCollationBaseline());
            }

            long costMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
            if (anomalies.isEmpty()) {
                log.info("数据治理任务完成: cleanup={}, costMs={}", cleanupSummary, costMs);
                return;
            }

            log.warn("数据治理任务发现 {} 个风险项: cleanup={}, costMs={}", anomalies.size(), cleanupSummary, costMs);
            anomalies.forEach(item -> log.warn("数据治理告警: {}", item));
            notifyIfNeeded(anomalies, cleanupSummary, costMs);
        } catch (Exception e) {
            log.error("数据治理任务执行失败", e);
        } finally {
            releaseLock(lockValue);
        }
    }

    private CleanupSummary cleanupOldData() {
        int safeUserViewDays = Math.max(userViewLogRetentionDays, 1);
        int safeRankingDays = Math.max(postRankingRetentionDays, 1);
        int safeReadDailyDays = Math.max(postReadDailyRetentionDays, 1);

        LocalDate viewCutoff = LocalDate.now().minusDays(safeUserViewDays);
        LocalDate rankingCutoff = LocalDate.now().minusDays(safeRankingDays);
        LocalDate readDailyCutoff = LocalDate.now().minusDays(safeReadDailyDays);

        int deletedUserViewLog = jdbcTemplate.update(
                "DELETE FROM t_user_view_log WHERE view_date < ?",
                viewCutoff
        );
        int deletedPostRanking = jdbcTemplate.update(
                "DELETE FROM t_post_ranking WHERE stat_date IS NOT NULL AND stat_date < ?",
                rankingCutoff
        );
        int deletedPostReadDaily = jdbcTemplate.update(
                "DELETE FROM t_post_read_daily WHERE read_date < ?",
                readDailyCutoff
        );

        log.info("数据保留治理完成: userViewLogDeleted={}, postRankingDeleted={}, postReadDailyDeleted={}, cutoffs=[{}, {}, {}]",
                deletedUserViewLog,
                deletedPostRanking,
                deletedPostReadDaily,
                viewCutoff,
                rankingCutoff,
                readDailyCutoff);

        return new CleanupSummary(deletedUserViewLog, deletedPostRanking, deletedPostReadDaily);
    }

    private List<String> checkCapacity() {
        List<String> anomalies = new ArrayList<>();
        checkTableRows("t_user_view_log", userViewLogWarnRows, anomalies);
        checkTableRows("t_post_ranking", postRankingWarnRows, anomalies);
        checkTableRows("t_login_log", loginLogWarnRows, anomalies);
        checkTableRows("t_audit_log", auditLogWarnRows, anomalies);
        return anomalies;
    }

    private void checkTableRows(String tableName, long warnRows, List<String> anomalies) {
        long threshold = Math.max(warnRows, 1L);
        long rows = countRows(tableName);
        if (rows >= threshold) {
            anomalies.add(String.format("容量预警: %s 行数=%d, 阈值=%d", tableName, rows, threshold));
        }
    }

    private List<String> checkWeakConstraintOrphans() {
        List<String> anomalies = new ArrayList<>();
        for (int i = 0; i < ORPHAN_CHECK_SQLS.size(); i++) {
            long count = queryCount(ORPHAN_CHECK_SQLS.get(i));
            if (count > 0) {
                anomalies.add(String.format("弱约束巡检: %s 存在孤儿数据 %d 条", ORPHAN_CHECK_NAMES.get(i), count));
            }
        }
        return anomalies;
    }

    private List<String> checkCollationBaseline() {
        String baseline = normalize(collationBaseline);
        if (baseline.isEmpty()) {
            return List.of("字符集巡检: collation-baseline 为空，已跳过");
        }

        StringBuilder sql = new StringBuilder("SELECT table_name, table_collation FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (");
        for (int i = 0; i < COLLATION_CHECK_TABLES.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), COLLATION_CHECK_TABLES.toArray());
        List<String> anomalies = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String tableName = String.valueOf(row.get("table_name"));
            String tableCollation = normalize(String.valueOf(row.get("table_collation")));
            if (!baseline.equalsIgnoreCase(tableCollation)) {
                anomalies.add(String.format("字符集基线偏差: %s 当前=%s, 期望=%s", tableName, tableCollation, baseline));
            }
        }
        return anomalies;
    }

    private long countRows(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        return queryCount(sql);
    }

    private long queryCount(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.error("执行计数SQL失败: sql={}, message={}", sql, e.getMessage());
            return 0L;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void notifyIfNeeded(List<String> anomalies, CleanupSummary cleanupSummary, long costMs) {
        if (!notifyOnAnomaly) {
            return;
        }
        String subject = String.format("[Weblog数据治理告警] %d 个风险项", anomalies.size());
        StringBuilder content = new StringBuilder();
        content.append("检测时间: ").append(LocalDateTime.now()).append('\n')
                .append("任务耗时(ms): ").append(costMs).append('\n')
                .append("清理结果: ").append(cleanupSummary).append('\n')
                .append("风险项: ").append('\n');
        anomalies.forEach(item -> content.append("- ").append(item).append('\n'));
        emailService.sendSecurityAlertAsync(subject, content.toString());
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(10));
        return Boolean.TRUE.equals(success) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        redisService.releaseLock(LOCK_KEY, lockValue);
    }

    private record CleanupSummary(int deletedUserViewLog, int deletedPostRanking, int deletedPostReadDaily) {
    }
}
