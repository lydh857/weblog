package com.blog.api.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.audit.AuditLogEntry;
import com.blog.infra.security.audit.AuditLogService;
import com.blog.infra.security.audit.AuditLogVO;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.LoginLogVO;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.SecurityRiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

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
  private final SecurityRiskControlService securityRiskControlService;

  @Operation(summary = "查询登录日志")
  @GetMapping("/login")
  @RateLimit(key = "admin-logs-login", capacity = 30, seconds = 60)
  public Result<IPage<LoginLogVO>> getLoginLogs(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String loginType,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String ip) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    return Result.success(loginLogService.getAllLoginLogs(pageParams.pageNum(), pageParams.pageSize(), email, loginType, result, ip));
  }

  @Operation(summary = "查询审计日志")
  @GetMapping("/audit")
  @RateLimit(key = "admin-logs-audit", capacity = 30, seconds = 60)
  public Result<IPage<AuditLogVO>> getAuditLogs(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String operation,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String ipAddress) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    IPage<AuditLogEntry> page = auditLogService.pageForAdmin(
            pageParams.pageNum(),
            pageParams.pageSize(),
            operation,
            module,
            username,
            ipAddress
    );
    return Result.success(page.convert(this::toAuditLogVO));
  }

  private AuditLogVO toAuditLogVO(AuditLogEntry entry) {
    AuditLogVO vo = new AuditLogVO();
    vo.setId(entry.getId());
    vo.setUserId(entry.getUserId());
    vo.setUsername(entry.getUsername());
    vo.setOperation(entry.getOperation());
    vo.setModule(entry.getModule());
    vo.setDescription(entry.getDescription());
    vo.setRequestMethod(entry.getRequestMethod());
    vo.setRequestUrl(entry.getRequestUrl());
    vo.setResponseCode(entry.getResponseCode());
    vo.setIpAddress(entry.getIpAddress());
    vo.setUserAgent(entry.getUserAgent());
    vo.setExecutionTime(entry.getExecutionTime());
    vo.setCreateTime(entry.getCreateTime());
    vo.setAdminActor(isAdminRequest(entry.getRequestUrl()));
    return vo;
  }

  private boolean isAdminRequest(String requestUrl) {
    return StringUtils.hasText(requestUrl) && requestUrl.startsWith("/api/admin/");
  }

  @Operation(summary = "手动封禁 IP")
  @PostMapping("/ip-block")
  @RateLimit(key = "admin-logs-ip-block", capacity = 20, seconds = 60)
  @AuditLog(module = "安全处置", operation = "BLOCK_IP", description = "手动封禁登录 IP")
  public Result<SecurityRiskControlService.IpBlockStatus> blockIp(@Valid @RequestBody IpBlockRequest request) {
    return Result.success(securityRiskControlService.blockIp(
            request.ip(),
            request.durationMinutes(),
            Boolean.TRUE.equals(request.permanent()),
            request.reason(),
            true
    ));
  }

  @Operation(summary = "手动封禁用户")
  @PostMapping("/user-block")
  @RateLimit(key = "admin-logs-user-block", capacity = 20, seconds = 60)
  @AuditLog(module = "安全处置", operation = "BLOCK_USER", description = "手动封禁用户")
  public Result<SecurityRiskControlService.UserBlockStatus> blockUser(@Valid @RequestBody UserBlockRequest request) {
    if (request.userId() == null && !StringUtils.hasText(request.email())) {
      throw new BusinessException(ResultCode.BAD_REQUEST, "用户ID或邮箱不能为空");
    }
    if (StringUtils.hasText(request.email())) {
      return Result.success(securityRiskControlService.blockUserByEmail(
              request.email(),
              request.durationMinutes(),
              Boolean.TRUE.equals(request.permanent()),
              request.reason(),
              true
      ));
    }
    return Result.success(securityRiskControlService.blockUser(
            request.userId(),
            request.durationMinutes(),
            Boolean.TRUE.equals(request.permanent()),
            request.reason(),
            true
    ));
  }

  @Operation(summary = "查询用户封禁状态")
  @GetMapping("/user-block")
  @RateLimit(key = "admin-logs-user-block-status", capacity = 60, seconds = 60)
  public Result<SecurityRiskControlService.UserBlockStatus> getUserBlockStatus(@RequestParam Long userId) {
    return Result.success(securityRiskControlService.getUserBlockStatus(userId));
  }

  @Operation(summary = "按邮箱查询用户封禁状态")
  @GetMapping("/user-block/by-email")
  @RateLimit(key = "admin-logs-user-block-status", capacity = 60, seconds = 60)
  public Result<SecurityRiskControlService.UserBlockStatus> getUserBlockStatusByEmail(@RequestParam String email) {
    return Result.success(securityRiskControlService.getUserBlockStatusByEmail(email));
  }

  @Operation(summary = "解除 IP 封禁")
  @DeleteMapping("/ip-block")
  @RateLimit(key = "admin-logs-ip-unblock", capacity = 20, seconds = 60)
  @AuditLog(module = "安全处置", operation = "UNBLOCK_IP", description = "解除登录 IP 封禁")
  public Result<Void> unblockIp(@RequestParam String ip) {
    securityRiskControlService.unblockIp(ip);
    return Result.success();
  }

  @Operation(summary = "查询 IP 封禁状态")
  @GetMapping("/ip-block")
  @RateLimit(key = "admin-logs-ip-block-status", capacity = 60, seconds = 60)
  public Result<SecurityRiskControlService.IpBlockStatus> getIpBlockStatus(@RequestParam String ip) {
    return Result.success(securityRiskControlService.getIpBlockStatus(ip));
  }

  @Operation(summary = "查询黑名单")
  @GetMapping("/blacklist")
  @RateLimit(key = "admin-logs-blacklist", capacity = 30, seconds = 60)
  public Result<IPage<SecurityRiskControlService.BlacklistItemVO>> getBlacklist(
          @RequestParam(defaultValue = "1") int pageNum,
          @RequestParam(defaultValue = "20") int pageSize,
          @RequestParam(required = false) String blockType,
          @RequestParam(required = false) String keyword,
          @RequestParam(required = false) String status) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    return Result.success(securityRiskControlService.pageBlacklist(
            pageParams.pageNum(),
            pageParams.pageSize(),
            blockType,
            keyword,
            status
    ));
  }

  @Operation(summary = "查询统一风控列表")
  @GetMapping("/risk-controls")
  @RateLimit(key = "admin-logs-risk-controls", capacity = 30, seconds = 60)
  public Result<IPage<SecurityRiskControlService.RiskControlItemVO>> getRiskControls(
          @RequestParam(defaultValue = "1") int pageNum,
          @RequestParam(defaultValue = "20") int pageSize,
          @RequestParam(required = false) String type,
          @RequestParam(required = false) String keyword,
          @RequestParam(required = false) String status) {
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    return Result.success(securityRiskControlService.pageRiskControls(
            pageParams.pageNum(),
            pageParams.pageSize(),
            type,
            keyword,
            status
    ));
  }

  @Operation(summary = "解除黑名单")
  @DeleteMapping("/blacklist")
  @RateLimit(key = "admin-logs-blacklist-unblock", capacity = 20, seconds = 60)
  @AuditLog(module = "安全处置", operation = "UNBLOCK", description = "解除黑名单")
  public Result<Void> unblockById(@RequestParam Long id) {
    securityRiskControlService.unblockById(id);
    return Result.success();
  }

  public record IpBlockRequest(
          @NotBlank(message = "IP 不能为空") String ip,
          @Min(value = 1, message = "封禁分钟数最小为1")
          @Max(value = 43200, message = "封禁分钟数最大为43200")
          Integer durationMinutes,
          Boolean permanent,
          String reason
  ) {
  }

  public record UserBlockRequest(
          @Min(value = 1, message = "用户ID无效")
          Long userId,
          String email,
          @Min(value = 1, message = "封禁分钟数最小为1")
          @Max(value = 43200, message = "封禁分钟数最大为43200")
          Integer durationMinutes,
          Boolean permanent,
          String reason
  ) {
  }
}
