package com.blog.api.scheduler;

import com.blog.infra.security.audit.AuditLogService;
import com.blog.system.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

  @Value("${blog.security.log-cleanup.login-log-retention-days:180}")
  private int loginLogRetentionDays;

  @Value("${blog.security.log-cleanup.audit-log-retention-days:180}")
  private int auditLogRetentionDays;

  /**
   * 默认每天凌晨 4:30 执行，错开其他凌晨任务
   */
  @Scheduled(cron = "${blog.security.log-cleanup.cron:0 30 4 * * ?}")
  public void cleanupSecurityLogs() {
    cleanupNow();
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
