package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.util.IpUtil;
import com.blog.common.result.Result;
import com.blog.common.util.ValidateUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.*;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.system.service.AuthService;
import com.blog.system.service.EmailCodeService;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.RememberTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * 用户端认证接口
 */
@Tag(name = "用户端-认证", description = "用户注册、登录、登出、验证码")
@RestController
@RequestMapping("/api/portal/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;
    private final CaptchaService captchaService;
    private final LoginLogService loginLogService;
    private final RememberTokenService rememberTokenService;
    private static final String PORTAL_REMEMBER_COOKIE = "Portal-Remember-Token";
    private static final Duration REMEMBER_COOKIE_TTL = Duration.ofDays(30);
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_CODE_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 128;

    @Operation(summary = "用户注册", description = "通过邮箱和密码注册新用户，需要邮箱验证码")
    @PostMapping("/register")
    @RateLimit(key = "register", capacity = 5, seconds = 60)
    @AuditLog(module = "认证", operation = "REGISTER", description = "用户注册")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req,
                                 @RequestParam String code,
                                 @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                 HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);
        authService.registerWithCode(req, code);
        return Result.success();
    }

    @Operation(summary = "用户登录（密码）", description = "通过邮箱和密码登录，需要滑块验证码，连续5次失败将锁定账户30分钟")
    @PostMapping("/login")
    @RateLimit(key = "login", capacity = 5, seconds = 60)
    @AuditLog(module = "认证", operation = "LOGIN", description = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);
        LoginResponse loginResponse = authService.login(req, clientIp, userAgent);

        syncRememberCookie(response, request, loginResponse.getRememberToken(), req.isRememberMe());
        loginResponse.setRememberToken(null);
        return Result.success(loginResponse);
    }

    @Operation(summary = "验证码登录", description = "通过邮箱验证码登录，首次登录自动创建账号")
    @PostMapping("/login-by-code")
    @RateLimit(key = "loginByCode", capacity = 5, seconds = 60)
    @AuditLog(module = "认证", operation = "LOGIN", description = "验证码登录")
    public Result<LoginResponse> loginByCode(@Valid @RequestBody CodeLoginRequest req,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        LoginResponse loginResponse = authService.loginByCode(req.getEmail(), req.getCode(), clientIp, userAgent);

        clearRememberCookie(response, request);
        loginResponse.setRememberToken(null);
        return Result.success(loginResponse);
    }

    @Operation(summary = "发送验证码", description = "发送邮箱验证码，60秒冷却")
    @PostMapping("/send-code")
    @RateLimit(key = "sendCode", capacity = 3, seconds = 60)
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest req,
                                 @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                 HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);
        String scene = req.getScene();
        if (!java.util.Set.of("login", "register", "bind", "change-email", "reset-pwd", "forgot-password").contains(scene)) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "无效的场景参数");
        }
        emailCodeService.sendCode(req.getEmail(), scene);
        return Result.success();
    }

    @Operation(summary = "刷新 Access Token", description = "使用 Refresh Token 刷新 Access Token")
    @PostMapping("/refresh")
    @RateLimit(key = "refreshToken", capacity = 10, seconds = 60)
    public Result<RefreshTokenResponse> refreshToken(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.UNAUTHORIZED, "Refresh token 无效或已过期");
        }
        StpUtil.renewTimeout(1800);
        return Result.success(new RefreshTokenResponse(true));
    }

    @Operation(summary = "用户登出", description = "注销当前登录状态，Token 失效")
    @PostMapping("/logout")
    @AuditLog(module = "认证", operation = "LOGOUT", description = "用户登出")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout();
        clearRememberCookie(response, request);
        return Result.success();
    }

    @Operation(summary = "Remember Token 自动登录", description = "使用 Remember Token 实现自动登录")
    @PostMapping("/remember-login")
    @RateLimit(key = "portal-remember-login", capacity = 10, seconds = 60)
    public Result<LoginResponse> rememberLogin(HttpServletRequest request,
                                               HttpServletResponse response) {
        String token = readCookie(request, PORTAL_REMEMBER_COOKIE);
        if (token == null || token.isBlank()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "token 不能为空");
        }
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        
        com.blog.system.dto.LoginVO vo = rememberTokenService.autoLogin(token, userAgent, clientIp);
        if (vo == null) {
            return Result.fail("Remember Token 无效或已过期");
        }
        
        // 记录成功登录
        loginLogService.recordLogin(vo.getUserId(), vo.getEmail(), "user", "success", "Remember Token 自动登录", clientIp, userAgent);

        // 轮换后的 Remember Token 仅通过 HttpOnly Cookie 返回
        writeRememberCookie(response, request, vo.getRememberToken(), REMEMBER_COOKIE_TTL);
        
        return Result.success(LoginResponse.builder()
                .success(true)
                .userId(vo.getUserId())
                .email(vo.getEmail())
                .nickname(vo.getNickname())
                .avatar(vo.getAvatar())
                .role(vo.getRole())
                .needBindEmail(false)
                .hasPassword(true)
                .rememberToken(null)
                .build());
    }

    @Operation(summary = "查询我的登录日志", description = "查看当前用户的登录历史")
    @GetMapping("/login-logs")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<LoginLogVO>> getMyLoginLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(loginLogService.getMyLoginLogs(pageNum, pageSize));
    }

    @Operation(summary = "检查邮箱可用性", description = "验证邮箱格式和域名MX记录")
    @PostMapping("/check-email")
    @RateLimit(key = "checkEmail", capacity = 10, seconds = 60)
    public Result<Void> checkEmail(@RequestBody Map<String, String> body) {
        String email = normalizeEmailField(body, "email");
        String domain = email.substring(email.indexOf('@') + 1);
        try {
            var env = new java.util.Hashtable<String, String>();
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            var ctx = new javax.naming.directory.InitialDirContext(env);
            var attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
            var mx = attrs.get("MX");
            if (mx == null || mx.size() == 0) {
                throw new com.blog.common.exception.BusinessException(
                        com.blog.common.result.ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
            }
            ctx.close();
        } catch (com.blog.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }
        return Result.success();
    }

    @Operation(summary = "忘记密码", description = "通过邮箱验证码重置密码，无需登录")
    @PostMapping("/forgot-password")
    @RateLimit(key = "forgotPassword", capacity = 5, seconds = 60)
    public Result<Void> forgotPassword(@RequestBody Map<String, String> body) {
        String email = normalizeEmailField(body, "email");
        String code = normalizeCodeField(body, "code");
        String password = getRequiredPassword(body, "password");
        authService.forgotPassword(email, code, password);
        return Result.success();
    }

    private String normalizeEmailField(Map<String, String> body, String field) {
        String email = getRequiredText(body, field, "邮箱不能为空");
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "邮箱长度不能超过" + MAX_EMAIL_LENGTH + "个字符");
        }
        if (!ValidateUtil.isValidEmail(email)) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "邮箱格式不正确");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    private String normalizeCodeField(Map<String, String> body, String field) {
        String code = getRequiredText(body, field, "验证码不能为空");
        if (code.length() > MAX_CODE_LENGTH) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "验证码格式不正确");
        }
        return code;
    }

    private String getRequiredPassword(Map<String, String> body, String field) {
        if (body == null) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.isBlank()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "新密码不能为空");
        }
        if (value.length() > MAX_PASSWORD_LENGTH) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_INVALID, "密码长度不能超过" + MAX_PASSWORD_LENGTH + "个字符");
        }
        return value;
    }

    private String getRequiredText(Map<String, String> body, String field, String message) {
        if (body == null) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.trim().isEmpty()) {
            throw new com.blog.common.exception.BusinessException(
                    com.blog.common.result.ResultCode.PARAM_MISSING, message);
        }
        return value.trim();
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
        ResponseCookie cookie = ResponseCookie.from(PORTAL_REMEMBER_COOKIE, token)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRememberCookie(HttpServletResponse response, HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(PORTAL_REMEMBER_COOKIE, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
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
}
