package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.util.PageParamUtil;
import com.blog.common.result.Result;
import com.blog.infra.security.audit.AuditLogEntry;
import com.blog.infra.security.audit.AuditLogService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.LoginLogVO;
import com.blog.system.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 日志中心
 */
@Tag(name = "管理端-日志中心", description = "登录日志与审计日志查询")
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

  private final LoginLogService loginLogService;
  private final AuditLogService auditLogService;

  @Operation(summary = "查询登录日志")
  @GetMapping("/login")
  @RateLimit(key = "admin-logs-login", capacity = 30, seconds = 60)
  public Result<IPage<LoginLogVO>> getLoginLogs(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String loginType,
      @RequestParam(required = false) String result) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    return Result.success(loginLogService.getAllLoginLogs(pageParams.pageNum(), pageParams.pageSize(), email, loginType, result));
  }

  @Operation(summary = "查询审计日志")
  @GetMapping("/audit")
  @RateLimit(key = "admin-logs-audit", capacity = 30, seconds = 60)
  public Result<IPage<AuditLogEntry>> getAuditLogs(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String operation,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String ipAddress) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    return Result.success(auditLogService.pageForAdmin(pageParams.pageNum(), pageParams.pageSize(), operation, module, username, ipAddress));
  }
}
