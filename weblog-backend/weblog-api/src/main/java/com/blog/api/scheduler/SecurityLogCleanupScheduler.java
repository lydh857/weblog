package com.blog.api.scheduler;

import com.blog.infra.security.audit.AuditLogService;
import com.blog.infra.security.audit.SecurityEventAuditService;
import com.blog.system.service.EmailService;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.SecurityRiskControlService;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全日志清理定时任务
 * 按保留天数清理登录日志和审计日志，控制数据库体积增长
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "blog.security.log-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityLogCleanupScheduler {

  private final LoginLogService loginLogService;
  private final AuditLogService auditLogService;
  private final StringRedisTemplate redisTemplate;
  private final EmailService emailService;
  private final JdbcTemplate jdbcTemplate;
  private final SystemConfigService systemConfigService;
  private final SecurityRiskControlService securityRiskControlService;
  private final SecurityEventAuditService securityEventAuditService;

  private static final String ATTACK_ALERT_SUPPRESS_KEY = "security:attack-alert:login-failed";
  private static final String ATTACK_ALERT_HOURLY_COUNT_KEY_PREFIX = "security:attack-alert:hourly:";
  private static final String ATTACK_ALERT_BASELINE_KEY = "security:attack-alert:baseline:ewma";
  private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
  private static final String LOGIN_LOG_RETENTION_DAYS_CONFIG_KEY = "login_log_retention_days";
  private static final String AUDIT_LOG_RETENTION_DAYS_CONFIG_KEY = "audit_log_retention_days";
  private static final int MIN_RETENTION_DAYS = 1;
  private static final int MAX_RETENTION_DAYS = 3650;

  private volatile long localLastAlertAtMs = 0L;
  private volatile double localAdaptiveBaseline = 0D;

  @Value("${blog.security.log-cleanup.login-log-retention-days:180}")
  private int loginLogRetentionDays;

  @Value("${blog.security.log-cleanup.audit-log-retention-days:180}")
  private int auditLogRetentionDays;

  @Value("${blog.security.log-cleanup.archive-enabled:true}")
  private boolean archiveEnabled;

  @Value("${blog.security.log-cleanup.archive-batch-size:20000}")
  private int archiveBatchSize;

  @Value("${blog.security.attack-alert.enabled:true}")
  private boolean attackAlertEnabled;

  @Value("${blog.security.attack-alert.window-minutes:5}")
  private int attackAlertWindowMinutes;

  @Value("${blog.security.attack-alert.total-failed-threshold:60}")
  private int attackAlertTotalFailedThreshold;

  @Value("${blog.security.attack-alert.per-ip-threshold:12}")
  private int attackAlertPerIpThreshold;

  @Value("${blog.security.attack-alert.top-ip-limit:5}")
  private int attackAlertTopIpLimit;

  @Value("${blog.security.attack-alert.suppress-minutes:10}")
  private int attackAlertSuppressMinutes;

  @Value("${blog.security.attack-alert.max-alerts-per-hour:6}")
  private int attackAlertMaxPerHour;

  @Value("${blog.security.attack-alert.slow-query-warn-ms:500}")
  private long attackAlertSlowQueryWarnMs;

  @Value("${blog.security.attack-alert.adaptive-threshold-enabled:true}")
  private boolean attackAlertAdaptiveThresholdEnabled;

  @Value("${blog.security.attack-alert.adaptive-threshold-alpha:0.25}")
  private double attackAlertAdaptiveAlpha;

  @Value("${blog.security.attack-alert.adaptive-threshold-multiplier:2.2}")
  private double attackAlertAdaptiveMultiplier;

  @Value("${blog.security.attack-alert.adaptive-threshold-min:60}")
  private int attackAlertAdaptiveMinThreshold;

  @Value("${blog.security.attack-alert.auto-ip-block-enabled:true}")
  private boolean attackAlertAutoIpBlockEnabled;

  @Value("${blog.security.attack-alert.auto-ip-block-minutes:60}")
  private int attackAlertAutoIpBlockMinutes;

  @Value("${blog.security.attack-alert.admin-log-url:http://127.0.0.1:3001/logs}")
  private String attackAlertAdminLogUrl;

  /**
   * 默认每天凌晨 4:30 执行，错开其他凌晨任务
   */
  @Scheduled(cron = "${blog.security.log-cleanup.cron:0 30 4 * * ?}")
  public void cleanupSecurityLogs() {
    cleanupNow();
  }

  /**
   * 登录失败激增检测（默认每 5 分钟）
   */
  @Scheduled(fixedDelayString = "${blog.security.attack-alert.interval-ms:300000}",
          initialDelayString = "${blog.security.attack-alert.initial-delay-ms:60000}")
  public void detectLoginAttackBurst() {
    if (!attackAlertEnabled) {
      return;
    }

    int safeWindowMinutes = Math.max(attackAlertWindowMinutes, 1);
    LocalDateTime since = LocalDateTime.now().minusMinutes(safeWindowMinutes);

    long countStartNs = System.nanoTime();
    long failedCount = loginLogService.countFailedLoginsSince(since);
    long countCostMs = Duration.ofNanos(System.nanoTime() - countStartNs).toMillis();
    warnSlowQuery("countFailedLoginsSince", countCostMs);

    int dynamicThreshold = resolveDynamicThreshold(failedCount);

    if (failedCount < dynamicThreshold) {
      return;
    }

    if (!shouldEmitAttackAlert()) {
      return;
    }

    long topIpStartNs = System.nanoTime();
    List<Map<String, Object>> hotIps = loginLogService.listTopFailedIps(
            since,
            Math.max(attackAlertPerIpThreshold, 1),
            Math.max(attackAlertTopIpLimit, 1)
    );
    long topIpCostMs = Duration.ofNanos(System.nanoTime() - topIpStartNs).toMillis();
    warnSlowQuery("listTopFailedIps", topIpCostMs);

    log.warn("检测到登录失败激增: windowMinutes={}, failedCount={}, staticThreshold={}, dynamicThreshold={}, hotIps={}",
            safeWindowMinutes,
            failedCount,
            attackAlertTotalFailedThreshold,
            dynamicThreshold,
            hotIps);

    List<String> autoBlockedIps = autoBlockSuspiciousIps(hotIps, safeWindowMinutes);

    if (allowAlertByHourlyQuota()) {
      String subject = String.format("[zhhhkl安全告警] 登录失败激增 (%d分钟内%d次)", safeWindowMinutes, failedCount);
      String hotIpText = hotIps.isEmpty()
              ? "无"
              : hotIps.stream().map(String::valueOf).collect(Collectors.joining("\n"));
      String blockedIpText = autoBlockedIps.isEmpty() ? "无" : String.join("\n", autoBlockedIps);
      String quickActionText = buildQuickActionText(hotIps);
      String content = String.format(
              "检测时间: %s\n窗口分钟: %d\n失败总数: %d\n静态阈值: %d\n动态阈值: %d\n单IP阈值: %d\ncount耗时(ms): %d\ntopIp耗时(ms): %d\n高风险IP: \n%s\n\n自动封禁IP: \n%s\n\n快捷处置入口: \n%s\n",
              LocalDateTime.now(),
              safeWindowMinutes,
              failedCount,
              attackAlertTotalFailedThreshold,
              dynamicThreshold,
              attackAlertPerIpThreshold,
              countCostMs,
              topIpCostMs,
              hotIpText,
              blockedIpText,
              quickActionText
      );
      emailService.sendSecurityAlertAsync(subject, content);
    } else {
      log.warn("攻击告警已触发但超过每小时上限，跳过邮件通知: maxPerHour={}", attackAlertMaxPerHour);
    }
  }

  private boolean shouldEmitAttackAlert() {
    int suppressMinutes = Math.max(attackAlertSuppressMinutes, 1);

    try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
              ATTACK_ALERT_SUPPRESS_KEY,
              String.valueOf(System.currentTimeMillis()),
              Duration.ofMinutes(suppressMinutes)
      );
      return Boolean.TRUE.equals(acquired);
    } catch (Exception e) {
      long now = System.currentTimeMillis();
      long suppressMillis = Duration.ofMinutes(suppressMinutes).toMillis();
      if (now - localLastAlertAtMs < suppressMillis) {
        return false;
      }
      localLastAlertAtMs = now;
      log.warn("攻击告警去重降级为本地模式: {}", e.getMessage());
      return true;
    }
  }

  private boolean allowAlertByHourlyQuota() {
    int maxPerHour = Math.max(attackAlertMaxPerHour, 1);
    String key = ATTACK_ALERT_HOURLY_COUNT_KEY_PREFIX + LocalDateTime.now().format(HOUR_FMT);
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count != null && count == 1L) {
        redisTemplate.expire(key, Duration.ofHours(2));
      }
      return count == null || count <= maxPerHour;
    } catch (Exception e) {
      log.warn("每小时告警配额检查失败，默认允许发送: {}", e.getMessage());
      return true;
    }
  }

  private int resolveDynamicThreshold(long failedCount) {
    int staticThreshold = Math.max(attackAlertTotalFailedThreshold, 1);
    if (!attackAlertAdaptiveThresholdEnabled) {
      return staticThreshold;
    }

    double alpha = attackAlertAdaptiveAlpha;
    if (alpha <= 0D || alpha > 1D) {
      alpha = 0.25D;
    }
    double multiplier = Math.max(attackAlertAdaptiveMultiplier, 1D);
    int minThreshold = Math.max(attackAlertAdaptiveMinThreshold, 1);

    double baseline = readAdaptiveBaseline();
    double nextBaseline = baseline <= 0D
            ? failedCount
            : baseline + alpha * (failedCount - baseline);
    writeAdaptiveBaseline(nextBaseline);

    int adaptiveThreshold = (int) Math.ceil(nextBaseline * multiplier);
    return Math.max(staticThreshold, Math.max(minThreshold, adaptiveThreshold));
  }

  private List<String> autoBlockSuspiciousIps(List<Map<String, Object>> hotIps, int safeWindowMinutes) {
    if (!attackAlertAutoIpBlockEnabled || hotIps == null || hotIps.isEmpty()) {
      return List.of();
    }

    int blockMinutes = Math.max(attackAlertAutoIpBlockMinutes, 1);
    List<String> blockedIpSummary = new ArrayList<>();

    for (Map<String, Object> row : hotIps) {
      String ip = normalizeIp(row.get("ip"));
      if (!StringUtils.hasText(ip)) {
        continue;
      }

      long failedCount = toLong(row.get("failedCount"));
      SecurityRiskControlService.IpBlockStatus beforeStatus = securityRiskControlService.getIpBlockStatus(ip);
      if (beforeStatus.blocked()) {
        continue;
      }
      String reason = String.format(
              "登录失败激增自动封禁（%d分钟窗口，失败次数=%d，阈值=%d）",
              safeWindowMinutes,
              failedCount,
              Math.max(attackAlertPerIpThreshold, 1)
      );

      SecurityRiskControlService.IpBlockStatus status =
              securityRiskControlService.blockIp(ip, blockMinutes, reason, false);
      if (!status.blocked()) {
        continue;
      }

      blockedIpSummary.add(String.format("%s（%d次，封禁剩余%ds）", ip, failedCount, status.remainingSeconds()));
      securityEventAuditService.recordEvent(
              "安全防护",
              "AUTO_BLOCK_IP",
              reason,
              null,
              null,
              ip,
              null,
              200,
              null,
              null
      );
    }

    return blockedIpSummary;
  }

  private String buildQuickActionText(List<Map<String, Object>> hotIps) {
    if (hotIps == null || hotIps.isEmpty()) {
      return "无";
    }

    String baseUrl = StringUtils.hasText(attackAlertAdminLogUrl)
            ? attackAlertAdminLogUrl.trim()
            : "http://127.0.0.1:3001/logs";
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }

    List<String> links = new ArrayList<>();
    for (Map<String, Object> row : hotIps) {
      String ip = normalizeIp(row.get("ip"));
      if (!StringUtils.hasText(ip)) {
        continue;
      }
      String link = baseUrl + "?tab=login&ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8);
      links.add(ip + " -> " + link);
    }
    return links.isEmpty() ? "无" : String.join("\n", links);
  }

  private String normalizeIp(Object ipObj) {
    if (ipObj == null) {
      return null;
    }
    String raw = String.valueOf(ipObj).trim();
    return raw.isEmpty() ? null : raw;
  }

  private long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private double readAdaptiveBaseline() {
    try {
      String raw = redisTemplate.opsForValue().get(ATTACK_ALERT_BASELINE_KEY);
      if (raw != null && !raw.isBlank()) {
        return Double.parseDouble(raw.trim());
      }
    } catch (Exception e) {
      log.warn("读取攻击告警动态阈值基线失败，降级本地: {}", e.getMessage());
    }
    return localAdaptiveBaseline;
  }

  private void writeAdaptiveBaseline(double baseline) {
    localAdaptiveBaseline = baseline;
    try {
      redisTemplate.opsForValue().set(
              ATTACK_ALERT_BASELINE_KEY,
              String.format(java.util.Locale.ROOT, "%.4f", baseline),
              Duration.ofDays(2)
      );
    } catch (Exception e) {
      log.warn("写入攻击告警动态阈值基线失败，降级本地: {}", e.getMessage());
    }
  }

  private void warnSlowQuery(String queryName, long costMs) {
    long warnThreshold = Math.max(attackAlertSlowQueryWarnMs, 1L);
    if (costMs >= warnThreshold) {
      log.warn("攻击告警链路慢查询: query={}, costMs={}, warnThresholdMs={}", queryName, costMs, warnThreshold);
    }
  }

  public CleanupResult cleanupNow() {
    int loginDeleted = cleanupLoginLogsNow();
    int auditDeleted = cleanupAuditLogsNow();
    log.info("安全日志清理完成: loginDeleted={}, auditDeleted={}", loginDeleted, auditDeleted);
    return new CleanupResult(loginDeleted, auditDeleted);
  }

  public int cleanupLoginLogsNow() {
    int retentionDays = resolveRetentionDays(LOGIN_LOG_RETENTION_DAYS_CONFIG_KEY, loginLogRetentionDays);
    try {
      int deleted = cleanupLoginLogsWithArchive(retentionDays);
      log.info("登录日志清理完成: retentionDays={}, loginDeleted={}", retentionDays, deleted);
      return deleted;
    } catch (Exception e) {
      log.error("登录日志清理失败: retentionDays={}", retentionDays, e);
      throw new IllegalStateException("登录日志清理失败", e);
    }
  }

  public int cleanupAuditLogsNow() {
    int retentionDays = resolveRetentionDays(AUDIT_LOG_RETENTION_DAYS_CONFIG_KEY, auditLogRetentionDays);
    try {
      int deleted = cleanupAuditLogsWithArchive(retentionDays);
      log.info("审计日志清理完成: retentionDays={}, auditDeleted={}", retentionDays, deleted);
      return deleted;
    } catch (Exception e) {
      log.error("审计日志清理失败: retentionDays={}", retentionDays, e);
      throw new IllegalStateException("审计日志清理失败", e);
    }
  }

  private int resolveRetentionDays(String configKey, int fallbackValue) {
    int configuredDays = systemConfigService.getIntValue(configKey, fallbackValue);
    int safeDays = Math.max(MIN_RETENTION_DAYS, Math.min(configuredDays, MAX_RETENTION_DAYS));
    if (safeDays != configuredDays) {
      log.warn("日志保留天数越界，已自动纠正: key={}, configured={}, safe={}", configKey, configuredDays, safeDays);
    }
    return safeDays;
  }

  private int cleanupLoginLogsWithArchive(int retentionDays) {
    if (!archiveEnabled || !tableExists("t_login_log_archive")) {
      return loginLogService.cleanupOldLogs(retentionDays);
    }

    LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 1));
    int safeBatchSize = Math.max(archiveBatchSize, 1000);
    int totalDeleted = 0;

    while (true) {
      int archived = jdbcTemplate.update(
              """
              INSERT IGNORE INTO t_login_log_archive
              (origin_id, user_id, email, login_type, result, fail_reason, ip, user_agent, create_time, archived_time)
              SELECT id, user_id, email, login_type, result, fail_reason, ip, user_agent, create_time, NOW()
              FROM t_login_log
              WHERE create_time < ?
              ORDER BY id
              LIMIT ?
              """,
              cutoff,
              safeBatchSize
      );

      int deleted = jdbcTemplate.update(
              """
              DELETE FROM t_login_log
              WHERE id IN (
                SELECT id FROM (
                  SELECT l.id
                  FROM t_login_log l
                  INNER JOIN t_login_log_archive a ON a.origin_id = l.id
                  WHERE l.create_time < ?
                  ORDER BY l.id
                  LIMIT ?
                ) as batch_ids
              )
              """,
              cutoff,
              safeBatchSize
      );

      totalDeleted += deleted;
      if (archived <= 0 && deleted <= 0) {
        break;
      }
      if (archived < safeBatchSize && deleted < safeBatchSize) {
        break;
      }
    }

    log.info("登录日志归档清理完成: cutoff={}, deleted={}", cutoff, totalDeleted);
    return totalDeleted;
  }

  private int cleanupAuditLogsWithArchive(int retentionDays) {
    if (!archiveEnabled || !tableExists("t_audit_log_archive")) {
      return auditLogService.cleanupOldLogs(retentionDays);
    }

    LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 1));
    int safeBatchSize = Math.max(archiveBatchSize, 1000);
    int totalDeleted = 0;

    while (true) {
      int archived = jdbcTemplate.update(
              """
              INSERT IGNORE INTO t_audit_log_archive
              (origin_id, user_id, username, operation, module, description, request_method, request_url,
               request_params, response_code, ip_address, user_agent, execution_time, create_time, archived_time)
              SELECT id, user_id, username, operation, module, description, request_method, request_url,
                     request_params, response_code, ip_address, user_agent, execution_time, create_time, NOW()
              FROM t_audit_log
              WHERE create_time < ?
              ORDER BY id
              LIMIT ?
              """,
              cutoff,
              safeBatchSize
      );

      int deleted = jdbcTemplate.update(
              """
              DELETE FROM t_audit_log
              WHERE id IN (
                SELECT id FROM (
                  SELECT a.id
                  FROM t_audit_log a
                  INNER JOIN t_audit_log_archive ar ON ar.origin_id = a.id
                  WHERE a.create_time < ?
                  ORDER BY a.id
                  LIMIT ?
                ) as batch_ids
              )
              """,
              cutoff,
              safeBatchSize
      );

      totalDeleted += deleted;
      if (archived <= 0 && deleted <= 0) {
        break;
      }
      if (archived < safeBatchSize && deleted < safeBatchSize) {
        break;
      }
    }

    log.info("审计日志归档清理完成: cutoff={}, deleted={}", cutoff, totalDeleted);
    return totalDeleted;
  }

  private boolean tableExists(String tableName) {
    try {
      Long exists = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
              Long.class,
              tableName
      );
      return exists != null && exists > 0;
    } catch (Exception e) {
      log.warn("检查归档表是否存在失败: table={}, message={}", tableName, e.getMessage());
      return false;
    }
  }

  public record CleanupResult(int loginDeleted, int auditDeleted) {}

  public record DeletedResult(int deleted) {}
}
