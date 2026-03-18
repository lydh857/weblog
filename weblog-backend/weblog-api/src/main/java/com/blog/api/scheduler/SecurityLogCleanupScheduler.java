package com.blog.api.scheduler;

import com.blog.infra.security.audit.AuditLogService;
import com.blog.system.service.EmailService;
import com.blog.system.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

  private static final String ATTACK_ALERT_SUPPRESS_KEY = "security:attack-alert:login-failed";
  private static final String ATTACK_ALERT_HOURLY_COUNT_KEY_PREFIX = "security:attack-alert:hourly:";
  private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

  private volatile long localLastAlertAtMs = 0L;

  @Value("${blog.security.log-cleanup.login-log-retention-days:180}")
  private int loginLogRetentionDays;

  @Value("${blog.security.log-cleanup.audit-log-retention-days:180}")
  private int auditLogRetentionDays;

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
    long failedCount = loginLogService.countFailedLoginsSince(since);

    if (failedCount < Math.max(attackAlertTotalFailedThreshold, 1)) {
      return;
    }

    if (!shouldEmitAttackAlert()) {
      return;
    }

    List<Map<String, Object>> hotIps = loginLogService.listTopFailedIps(
            since,
            Math.max(attackAlertPerIpThreshold, 1),
            Math.max(attackAlertTopIpLimit, 1)
    );

    log.warn("检测到登录失败激增: windowMinutes={}, failedCount={}, threshold={}, hotIps={}",
            safeWindowMinutes,
            failedCount,
            attackAlertTotalFailedThreshold,
            hotIps);

    if (allowAlertByHourlyQuota()) {
      String subject = String.format("[Weblog安全告警] 登录失败激增 (%d分钟内%d次)", safeWindowMinutes, failedCount);
      String hotIpText = hotIps.isEmpty()
              ? "无"
              : hotIps.stream().map(String::valueOf).collect(Collectors.joining("\n"));
      String content = String.format(
              "检测时间: %s\n窗口分钟: %d\n失败总数: %d\n触发阈值: %d\n单IP阈值: %d\n高风险IP: \n%s\n",
              LocalDateTime.now(),
              safeWindowMinutes,
              failedCount,
              attackAlertTotalFailedThreshold,
              attackAlertPerIpThreshold,
              hotIpText
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

  public CleanupResult cleanupNow() {
    try {
      int loginDeleted = loginLogService.cleanupOldLogs(loginLogRetentionDays);
      int auditDeleted = auditLogService.cleanupOldLogs(auditLogRetentionDays);
      log.info("安全日志清理完成: loginDeleted={}, auditDeleted={}", loginDeleted, auditDeleted);
      return new CleanupResult(loginDeleted, auditDeleted);
    } catch (Exception e) {
      log.error("安全日志清理失败", e);
      return new CleanupResult(0, 0);
    }
  }

  public record CleanupResult(int loginDeleted, int auditDeleted) {}
}
