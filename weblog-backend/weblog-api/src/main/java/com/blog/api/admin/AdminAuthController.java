package com.blog.api.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.result.Result;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.LoginRequest;
import com.blog.system.dto.LoginResponse;
import com.blog.system.dto.LoginLogVO;
import com.blog.system.dto.RefreshTokenResponse;
import com.blog.system.service.AuthService;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.RememberTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端认证接口
 */
@Tag(name = "管理端 - 认证", description = "管理员登录、登出")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;
    private final LoginLogService loginLogService;
    private final RememberTokenService rememberTokenService;

    @Operation(summary = "管理员登录", description = "管理员通过邮箱和密码登录，非 admin 角色将被拒绝")
    @PostMapping("/login")
    @RateLimit(key = "admin-login", capacity = 5, seconds = 60)
    @AuditLog(module = "管理端认证", operation = "LOGIN", description = "管理员登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        LoginResponse response = authService.adminLogin(req, clientIp, userAgent);
        return Result.success(response);
    }

    @Operation(summary = "刷新 Access Token", description = "使用 Refresh Token 刷新 Access Token")
    @PostMapping("/refresh")
    @RateLimit(key = "admin-refresh-token", capacity = 10, seconds = 60)
    public Result<RefreshTokenResponse> refreshToken() {
        if (!StpUtil.isLogin()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.UNAUTHORIZED, "Refresh token 无效或已过期");
        }
        StpUtil.renewTimeout(1800);
        return Result.success(new RefreshTokenResponse(true));
    }

    @Operation(summary = "管理员登出")
    @PostMapping("/logout")
    @AuditLog(module = "管理端认证", operation = "LOGOUT", description = "管理员登出")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "Remember Token 自动登录", description = "使用 Remember Token 实现自动登录")
    @PostMapping("/remember-login")
    public Result<LoginResponse> rememberLogin(@RequestBody java.util.Map<String, String> body,
                                               HttpServletRequest request) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "token 不能为空");
        }
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        com.blog.system.dto.LoginVO vo = rememberTokenService.autoLogin(token, userAgent, clientIp);
        if (vo == null) {
            return Result.fail("Remember Token 无效或已过期");
        }
        
        // 记录成功登录
        loginLogService.recordLogin(vo.getUserId(), vo.getEmail(), "admin", "success", "Remember Token 自动登录", clientIp, userAgent);
        
        return Result.success(LoginResponse.builder()
                .success(true)
                .userId(vo.getUserId())
                .email(vo.getEmail())
                .nickname(vo.getNickname())
                .avatar(vo.getAvatar())
                .role(vo.getRole())
                .needBindEmail(false)
                .hasPassword(true)
                .rememberToken(vo.getRememberToken())
                .build());
    }

    @Operation(summary = "撤销 Remember Token", description = "使指定 Token 失效（用于用户手动撤销设备）")
    @PostMapping("/revoke-token")
    @AuditLog(module = "管理端认证", operation = "REVOKE_TOKEN", description = "撤销 Remember Token")
    public Result<Void> revokeToken(@RequestParam String token) {
        rememberTokenService.invalidateToken(token);
        return Result.success();
    }

    @Operation(summary = "撤销所有设备的 Remember Token", description = "使用户的所有 Remember Token 失效（用于修改密码后）")
    @PostMapping("/revoke-all-tokens")
    @AuditLog(module = "管理端认证", operation = "REVOKE_ALL_TOKENS", description = "撤销所有 Remember Token")
    public Result<Void> revokeAllTokens() {
        Long userId = StpUtil.getLoginIdAsLong();
        rememberTokenService.invalidateAllUserTokens(userId);
        return Result.success();
    }

    @Operation(summary = "查询所有登录日志", description = "管理端查看所有用户的登录历史")
    @GetMapping("/login-logs")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<LoginLogVO>> getAllLoginLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String result) {
        return Result.success(loginLogService.getAllLoginLogs(pageNum, pageSize, email, loginType, result));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
