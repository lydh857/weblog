package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.util.IpUtil;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.ValidateUtil;
import com.blog.common.util.PageParamUtil;
import com.blog.infra.redis.RedisService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.system.dto.*;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.system.service.AuthService;
import com.blog.system.service.EmailCodeService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.net.IDN;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private final RedisService redisService;
    private final LoginLogService loginLogService;
    private final RememberTokenService rememberTokenService;
    private final SecurityRiskControlService securityRiskControlService;
    private final LoginSecurityPolicyService loginSecurityPolicyService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
    private static final String PORTAL_REMEMBER_COOKIE = "Portal-Remember-Token";
    private static final Duration REMEMBER_COOKIE_TTL = Duration.ofDays(30);
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_CODE_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_DOMAIN_LENGTH = 253;
    private static final int CHECK_EMAIL_DOMAIN_RATE_CAPACITY = 3;
    private static final int CHECK_EMAIL_DOMAIN_RATE_SECONDS = 60;
    private static final String CHECK_EMAIL_DOMAIN_RATE_KEY_PREFIX = "auth:check-email:domain:";
    private static final int AUTH_USER_RATE_SECONDS = 300;
    private static final String AUTH_USER_RATE_KEY_PREFIX = "auth:user-rate:";
    private static final int DNS_CACHE_VALID_SECONDS = 1800;
    private static final int DNS_CACHE_INVALID_SECONDS = 600;
    private static final String DNS_CACHE_KEY_PREFIX = "auth:check-email:dns-cache:";
    private static final String DNS_FAIL_KEY = "auth:check-email:dns-fail";
    private static final String DNS_CIRCUIT_OPEN_KEY = "auth:check-email:dns-circuit:open";
    private static final int DNS_FAIL_THRESHOLD = 6;
    private static final int DNS_FAIL_WINDOW_SECONDS = 60;
    private static final int DNS_CIRCUIT_OPEN_SECONDS = 120;
    private static final Set<String> INTERNAL_DOMAIN_SUFFIXES = Set.of(
            "localhost", "local", "internal", "lan", "home", "localdomain", "corp"
    );
    private static final String SEND_CODE_RATE_LIMIT_KEY = "send_code_rate_limit";
    private static final String FORGOT_PASSWORD_RATE_LIMIT_KEY = "forgot_password_rate_limit";
    private static final String REGISTER_RATE_LIMIT_KEY = "register_rate_limit";
    private static final String CHECK_EMAIL_RATE_LIMIT_KEY = "check_email_rate_limit";
    private static final String CAPTCHA_SCENE_REGISTER = "register";
    private static final String CAPTCHA_SCENE_LOGIN_PASSWORD = "login-password";
    private static final String CAPTCHA_SCENE_LOGIN_CODE = "login-code";
    private static final String CAPTCHA_SCENE_SEND_CODE_PREFIX = "send-code:";
    private static final String CAPTCHA_SCENE_CHECK_EMAIL = "check-email";
    private static final String CAPTCHA_SCENE_FORGOT_PASSWORD = "forgot-password";

    @Operation(summary = "用户注册", description = "通过邮箱和密码注册新用户，需要邮箱验证码")
    @PostMapping("/register")
    @RateLimit(key = "register", capacity = 5, seconds = 60)
    @AuditLog(module = "认证", operation = "REGISTER", description = "用户注册")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req,
                                 @RequestParam String code,
                                 @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                 HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        securityRiskControlService.assertIpAllowed(clientIp, "portal-register", request.getHeader("User-Agent"));
        dynamicRateLimitPolicyService.enforcePerIp(
                "register",
                REGISTER_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                clientIp,
                "注册请求过于频繁，请稍后再试"
        );
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_REGISTER);

        String email = normalizeEmailValue(req.getEmail(), "邮箱不能为空");
        req.setEmail(email);
        enforceEmailActionRateLimit("register", email, 8, AUTH_USER_RATE_SECONDS);

        authService.registerWithCode(req, code);
        return Result.success();
    }

    @Operation(summary = "用户登录（密码）", description = "通过邮箱和密码登录，需要滑块验证码")
    @PostMapping("/login")
    @AuditLog(module = "认证", operation = "LOGIN", description = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        securityRiskControlService.assertIpAllowed(clientIp, "portal-login", userAgent);
        loginSecurityPolicyService.enforceLoginRateLimit("portal-login", clientIp);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_LOGIN_PASSWORD);

        String email = normalizeEmailValue(req.getEmail(), "邮箱不能为空");
        req.setEmail(email);
        enforceEmailActionRateLimit("login-password", email, 10, AUTH_USER_RATE_SECONDS);

        LoginResponse loginResponse = authService.login(req, clientIp, userAgent);

        syncRememberCookie(response, request, loginResponse.getRememberToken(), req.isRememberMe());
        loginResponse.setRememberToken(null);
        return Result.success(loginResponse);
    }

    @Operation(summary = "验证码登录", description = "通过邮箱验证码登录，需要滑块验证码，首次登录自动创建账号")
    @PostMapping("/login-by-code")
    @AuditLog(module = "认证", operation = "LOGIN", description = "验证码登录")
    public Result<LoginResponse> loginByCode(@Valid @RequestBody CodeLoginRequest req,
                                              @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        securityRiskControlService.assertIpAllowed(clientIp, "portal-login-by-code", userAgent);
        loginSecurityPolicyService.enforceLoginRateLimit("portal-login-by-code", clientIp);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_LOGIN_CODE);

        String email = normalizeEmailValue(req.getEmail(), "邮箱不能为空");
        req.setEmail(email);
        enforceEmailActionRateLimit("login-code", email, 10, AUTH_USER_RATE_SECONDS);

        LoginResponse loginResponse = authService.loginByCode(email, req.getCode(), clientIp, userAgent);

        clearRememberCookie(response, request);
        loginResponse.setRememberToken(null);
        return Result.success(loginResponse);
    }

    @Operation(summary = "发送验证码", description = "发送邮箱验证码，60秒冷却")
    @PostMapping("/send-code")
    @RateLimit(key = "sendCode", capacity = 120, seconds = 60)
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest req,
                                 @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                 HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        securityRiskControlService.assertIpAllowed(clientIp, "portal-send-code", request.getHeader("User-Agent"));
        dynamicRateLimitPolicyService.enforcePerIp(
                "sendCode",
                SEND_CODE_RATE_LIMIT_KEY,
                3,
                1,
                60,
                60,
                clientIp,
                "验证码发送过于频繁，请稍后再试"
        );
        String email = normalizeEmailValue(req.getEmail(), "邮箱不能为空");
        req.setEmail(email);

        String scene = req.getScene() == null ? "" : req.getScene().trim().toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("login", "register", "bind", "change-email", "reset-pwd", "forgot-password").contains(scene)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "无效的场景参数");
        }
        req.setScene(scene);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_SEND_CODE_PREFIX + scene);
        enforceEmailActionRateLimit("send-code:" + scene, email, 6, AUTH_USER_RATE_SECONDS);

        emailCodeService.sendCode(email, scene);
        return Result.success();
    }

    @Operation(summary = "刷新 Access Token", description = "使用 Refresh Token 刷新 Access Token")
    @PostMapping("/refresh")
    @RateLimit(key = "refreshToken", capacity = 10, seconds = 60)
    public Result<RefreshTokenResponse> refreshToken(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh token 无效或已过期");
        }
        StpUtil.renewTimeout(1800);
        return Result.success(new RefreshTokenResponse(true));
    }

    @Operation(summary = "用户登出", description = "注销当前登录状态，Token 失效")
    @PostMapping("/logout")
    @RateLimit(key = "logout", capacity = 30, seconds = 60)
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
            throw new BusinessException(ResultCode.PARAM_MISSING, "token 不能为空");
        }
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        securityRiskControlService.assertIpAllowed(clientIp, "portal-remember-login", userAgent);
        
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
    @RateLimit(key = "portal-login-logs", capacity = 30, seconds = 60)
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<LoginLogVO>> getMyLoginLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        return Result.success(loginLogService.getMyLoginLogs(pageParams.pageNum(), pageParams.pageSize()));
    }

    @Operation(summary = "检查邮箱可用性", description = "验证邮箱格式和域名MX记录")
    @PostMapping("/check-email")
    @RateLimit(key = "checkEmail", capacity = 10, seconds = 60)
    public Result<Void> checkEmail(@RequestBody Map<String, String> body,
                                   @RequestHeader(value = "X-Captcha-Token", required = false) String verifyToken,
                                   HttpServletRequest request) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "缺少验证码令牌");
        }

        String clientIp = IpUtil.getClientIp(request);
        securityRiskControlService.assertIpAllowed(clientIp, "portal-check-email", request.getHeader("User-Agent"));
        dynamicRateLimitPolicyService.enforcePerIp(
                "checkEmail",
                CHECK_EMAIL_RATE_LIMIT_KEY,
                10,
                1,
                60,
                60,
                clientIp,
                "邮箱校验请求过于频繁，请稍后再试"
        );
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_CHECK_EMAIL);

        String email = normalizeEmailField(body, "email");
        enforceEmailActionRateLimit("check-email", email, 8, AUTH_USER_RATE_SECONDS);

        String domain = normalizeAndValidateDomain(email.substring(email.indexOf('@') + 1));
        enforceCheckEmailDomainRateLimit(clientIp, domain);

        if (!checkDomainMxWithProtection(domain)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }
        return Result.success();
    }

    @Operation(summary = "忘记密码", description = "通过邮箱验证码重置密码，无需登录，需滑块验证码")
    @PostMapping("/forgot-password")
    @RateLimit(key = "forgotPassword", capacity = 120, seconds = 60)
    public Result<Void> forgotPassword(@RequestBody Map<String, String> body,
                                       @RequestHeader(value = "X-Captcha-Token") String verifyToken,
                                       HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        securityRiskControlService.assertIpAllowed(clientIp, "portal-forgot-password", request.getHeader("User-Agent"));
        dynamicRateLimitPolicyService.enforcePerIp(
                "forgotPassword",
                FORGOT_PASSWORD_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                clientIp,
                "重置密码请求过于频繁，请稍后再试"
        );
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp, CAPTCHA_SCENE_FORGOT_PASSWORD);
        String email = normalizeEmailField(body, "email");
        enforceEmailActionRateLimit("forgot-password", email, 6, AUTH_USER_RATE_SECONDS);

        String code = normalizeCodeField(body, "code");
        String password = getRequiredPassword(body, "password");
        authService.forgotPassword(email, code, password);
        return Result.success();
    }

    private String normalizeEmailValue(String email, String emptyMessage) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, emptyMessage);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱长度不能超过" + MAX_EMAIL_LENGTH + "个字符");
        }
        if (!ValidateUtil.isValidEmail(normalized)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeEmailField(Map<String, String> body, String field) {
        return normalizeEmailValue(getRequiredText(body, field, "邮箱不能为空"), "邮箱不能为空");
    }

    private String normalizeAndValidateDomain(String rawDomain) {
        String domain = rawDomain == null ? "" : rawDomain.trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }

        String asciiDomain;
        try {
            asciiDomain = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }

        if (asciiDomain.isEmpty()
                || asciiDomain.length() > MAX_DOMAIN_LENGTH
                || asciiDomain.startsWith(".")
                || asciiDomain.endsWith(".")
                || asciiDomain.contains("..")
                || !asciiDomain.contains(".")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }

        if (!asciiDomain.matches("^[a-z0-9.-]+$") || hasInvalidDomainLabel(asciiDomain)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }

        if (isIpLiteral(asciiDomain) || isInternalDomain(asciiDomain)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "邮箱域名无效，请检查邮箱地址");
        }

        return asciiDomain;
    }

    private boolean hasInvalidDomainLabel(String domain) {
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return true;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalDomain(String domain) {
        for (String suffix : INTERNAL_DOMAIN_SUFFIXES) {
            if (domain.equals(suffix) || domain.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpLiteral(String domain) {
        if (domain.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$")) {
            String[] parts = domain.split("\\.");
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        }
        return domain.startsWith("[") && domain.endsWith("]");
    }

    private void enforceCheckEmailDomainRateLimit(String clientIp, String domain) {
        String key = CHECK_EMAIL_DOMAIN_RATE_KEY_PREFIX + clientIp + ":" + domain;
        long count = redisService.incrementWithExpire(key, CHECK_EMAIL_DOMAIN_RATE_SECONDS);
        if (count > CHECK_EMAIL_DOMAIN_RATE_CAPACITY) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "请求过于频繁，请稍后再试");
        }
    }

    private void enforceEmailActionRateLimit(String action, String email, int capacity, int seconds) {
        String key = AUTH_USER_RATE_KEY_PREFIX + action + ":" + email;
        long count = redisService.incrementWithExpire(key, seconds);
        if (count > capacity) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "请求过于频繁，请稍后再试");
        }
    }

    private boolean checkDomainMxWithProtection(String domain) {
        if (Boolean.TRUE.equals(redisService.hasKey(DNS_CIRCUIT_OPEN_KEY))) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "邮箱校验服务繁忙，请稍后再试");
        }

        String cacheKey = DNS_CACHE_KEY_PREFIX + domain;
        String cached = redisService.get(cacheKey);
        if ("1".equals(cached)) {
            return true;
        }
        if ("0".equals(cached)) {
            return false;
        }

        try {
            boolean hasMx = queryDomainHasMx(domain);
            redisService.set(cacheKey, hasMx ? "1" : "0", hasMx ? DNS_CACHE_VALID_SECONDS : DNS_CACHE_INVALID_SECONDS, TimeUnit.SECONDS);
            redisService.delete(DNS_FAIL_KEY);
            return hasMx;
        } catch (javax.naming.NamingException e) {
            if (isDnsInfraException(e)) {
                openDnsCircuitIfNeeded();
                throw new BusinessException(ResultCode.RATE_LIMIT, "邮箱校验服务繁忙，请稍后再试");
            }

            redisService.set(cacheKey, "0", DNS_CACHE_INVALID_SECONDS, TimeUnit.SECONDS);
            return false;
        } catch (Exception e) {
            openDnsCircuitIfNeeded();
            throw new BusinessException(ResultCode.RATE_LIMIT, "邮箱校验服务繁忙，请稍后再试");
        }
    }

    private boolean queryDomainHasMx(String domain) throws javax.naming.NamingException {
        var env = new java.util.Hashtable<String, String>();
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        javax.naming.directory.InitialDirContext ctx = null;
        try {
            ctx = new javax.naming.directory.InitialDirContext(env);
            var attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
            var mx = attrs.get("MX");
            return mx != null && mx.size() > 0;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                    // ignore close exception
                }
            }
        }
    }

    private boolean isDnsInfraException(javax.naming.NamingException e) {
        return e instanceof javax.naming.CommunicationException
                || e instanceof javax.naming.ServiceUnavailableException
                || e instanceof javax.naming.TimeLimitExceededException;
    }

    private void openDnsCircuitIfNeeded() {
        long failCount = redisService.incrementWithExpire(DNS_FAIL_KEY, DNS_FAIL_WINDOW_SECONDS);
        if (failCount >= DNS_FAIL_THRESHOLD) {
            redisService.set(DNS_CIRCUIT_OPEN_KEY, "1", DNS_CIRCUIT_OPEN_SECONDS, TimeUnit.SECONDS);
            redisService.delete(DNS_FAIL_KEY);
        }
    }

    private String normalizeCodeField(Map<String, String> body, String field) {
        String code = getRequiredText(body, field, "验证码不能为空");
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "验证码格式不正确");
        }
        return code;
    }

    private String getRequiredPassword(Map<String, String> body, String field) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "新密码不能为空");
        }
        if (value.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "密码长度不能超过" + MAX_PASSWORD_LENGTH + "个字符");
        }
        return value;
    }

    private String getRequiredText(Map<String, String> body, String field, String message) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请求参数不能为空");
        }
        String value = body.get(field);
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, message);
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
                .sameSite("Strict")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRememberCookie(HttpServletResponse response, HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(PORTAL_REMEMBER_COOKIE, "")
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
}
