package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.util.IpUtil;
import com.blog.common.result.Result;
import com.blog.common.util.PageParamUtil;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.LoginRequest;
import com.blog.system.dto.LoginResponse;
import com.blog.system.dto.LoginLogVO;
import com.blog.system.dto.RefreshTokenResponse;
import com.blog.system.service.AuthService;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.LoginSecurityPolicyService;
import com.blog.system.service.RememberTokenService;
import com.blog.system.service.SecurityRiskControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 管理端认证接口
 */
@Tag(name = "管理端 - 认证", description = "管理员登录、登出")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String LEGACY_LOCAL_UPLOAD_PREFIX = "http://localhost:9091/uploads";

    private final AuthService authService;
    private final LoginLogService loginLogService;
    private final RememberTokenService rememberTokenService;
    private final CaptchaService captchaService;
    private final SecurityRiskControlService securityRiskControlService;
    private final LoginSecurityPolicyService loginSecurityPolicyService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
    private static final String ADMIN_REMEMBER_COOKIE = "Admin-Remember-Token";
    private static final Duration REMEMBER_COOKIE_TTL = Duration.ofDays(30);
    private static final String ADMIN_LOGIN_RATE_LIMIT_KEY = "admin_login_rate_limit";
    private static final String ADMIN_REVOKE_TOKEN_RATE_LIMIT_KEY = "admin_revoke_token_rate_limit";
    private static final String ADMIN_REVOKE_ALL_TOKENS_RATE_LIMIT_KEY = "admin_revoke_all_tokens_rate_limit";

    @Value("${blog.upload.base-url:http://localhost:9091/uploads}")
    private String uploadBaseUrl;

    @Operation(summary = "管理员登录", description = "管理员通过邮箱和密码登录，非 admin 角色将被拒绝")
    @PostMapping("/login")
    @RateLimit(key = "admin-login", capacity = 120, seconds = 60)
    @AuditLog(module = "管理端认证", operation = "LOGIN", description = "管理员登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        securityRiskControlService.assertIpAllowed(clientIp, "admin-login", userAgent);
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-login",
                ADMIN_LOGIN_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                clientIp,
                "管理员登录请求过于频繁，请稍后再试"
        );
        loginSecurityPolicyService.enforceLoginRateLimit("admin-login", clientIp);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);
        LoginResponse loginResponse = authService.adminLogin(req, clientIp, userAgent);
        loginResponse.setAvatar(normalizeLegacyUploadUrl(loginResponse.getAvatar()));

        syncRememberCookie(response, request, loginResponse.getRememberToken(), req.isRememberMe());
        loginResponse.setRememberToken(null);
        return Result.success(loginResponse);
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
    @RateLimit(key = "admin-logout", capacity = 30, seconds = 60)
    @AuditLog(module = "管理端认证", operation = "LOGOUT", description = "管理员登出")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout();
        clearRememberCookie(response, request);
        return Result.success();
    }

    @Operation(summary = "Remember Token 自动登录", description = "使用 Remember Token 实现自动登录")
    @PostMapping("/remember-login")
    @RateLimit(key = "admin-remember-login", capacity = 10, seconds = 60)
    public Result<LoginResponse> rememberLogin(HttpServletRequest request,
                                               HttpServletResponse response) {
        String token = readCookie(request, ADMIN_REMEMBER_COOKIE);
        if (token == null || token.isBlank()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "token 不能为空");
        }
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        securityRiskControlService.assertIpAllowed(clientIp, "admin-remember-login", userAgent);
        
        com.blog.system.dto.LoginVO vo = rememberTokenService.autoLogin(token, userAgent, clientIp);
        if (vo == null) {
            return Result.fail("Remember Token 无效或已过期");
        }

        if (!"admin".equals(vo.getRole())) {
            rememberTokenService.invalidateToken(token);
            return Result.fail("无管理员权限");
        }
        
        // 记录成功登录
        loginLogService.recordLogin(vo.getUserId(), vo.getEmail(), "admin", "success", "Remember Token 自动登录", clientIp, userAgent);

        // 轮换后的 Remember Token 仅通过 HttpOnly Cookie 返回
        writeRememberCookie(response, request, vo.getRememberToken(), REMEMBER_COOKIE_TTL);
        
        return Result.success(LoginResponse.builder()
                .success(true)
                .userId(vo.getUserId())
                .email(vo.getEmail())
                .nickname(vo.getNickname())
                .avatar(normalizeLegacyUploadUrl(vo.getAvatar()))
                .role(vo.getRole())
                .needBindEmail(false)
                .hasPassword(true)
                .rememberToken(null)
                .build());
    }

    @Operation(summary = "撤销 Remember Token", description = "使指定 Token 失效（用于用户手动撤销设备）")
    @PostMapping("/revoke-token")
    @RateLimit(key = "admin-revoke-token", capacity = 120, seconds = 60)
    @AuditLog(module = "管理端认证", operation = "REVOKE_TOKEN", description = "撤销 Remember Token")
    public Result<Void> revokeToken(@Valid @RequestBody RevokeTokenRequest request,
                                    HttpServletRequest httpRequest) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-revoke-token",
                ADMIN_REVOKE_TOKEN_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                IpUtil.getClientIp(httpRequest),
                "撤销令牌请求过于频繁，请稍后再试"
        );
        rememberTokenService.invalidateToken(request.token());
        return Result.success();
    }

    @Operation(summary = "撤销所有设备的 Remember Token", description = "使用户的所有 Remember Token 失效（用于修改密码后）")
    @PostMapping("/revoke-all-tokens")
    @RateLimit(key = "admin-revoke-all-tokens", capacity = 120, seconds = 60)
    @AuditLog(module = "管理端认证", operation = "REVOKE_ALL_TOKENS", description = "撤销所有 Remember Token")
    public Result<Void> revokeAllTokens(HttpServletRequest request) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-revoke-all-tokens",
                ADMIN_REVOKE_ALL_TOKENS_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                IpUtil.getClientIp(request),
                "撤销全部令牌请求过于频繁，请稍后再试"
        );
        Long userId = StpUtil.getLoginIdAsLong();
        rememberTokenService.invalidateAllUserTokens(userId);
        return Result.success();
    }

    @Operation(summary = "查询所有登录日志", description = "管理端查看所有用户的登录历史")
    @GetMapping("/login-logs")
    @RateLimit(key = "admin-login-logs", capacity = 30, seconds = 60)
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<LoginLogVO>> getAllLoginLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String ip) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        return Result.success(loginLogService.getAllLoginLogs(pageParams.pageNum(), pageParams.pageSize(), email, loginType, result, ip));
    }

    private void syncRememberCookie(HttpServletResponse response,
                                    HttpServletRequest request,
                                    String token,
                                    boolean rememberMe) {
        if (rememberMe && token != null && !token.isBlank()) {
            writeRememberCookie(response, request, token, REMEMBER_COOKIE_TTL);
            return;
        }
        clearRememberCookie(response, request);
    }

    private void writeRememberCookie(HttpServletResponse response,
                                     HttpServletRequest request,
                                     String token,
                                     Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(ADMIN_REMEMBER_COOKIE, token)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Strict")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRememberCookie(HttpServletResponse response, HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(ADMIN_REMEMBER_COOKIE, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto);
    }

    private String normalizeLegacyUploadUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        if (!rawValue.contains(LEGACY_LOCAL_UPLOAD_PREFIX)) {
            return rawValue;
        }
        String normalizedBase = normalizeUploadBaseUrl(uploadBaseUrl);
        return rawValue.replace(LEGACY_LOCAL_UPLOAD_PREFIX, normalizedBase);
    }

    private String normalizeUploadBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return LEGACY_LOCAL_UPLOAD_PREFIX;
        }
        String normalized = rawBaseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record RevokeTokenRequest(@NotBlank(message = "token 不能为空") String token) {}
}
