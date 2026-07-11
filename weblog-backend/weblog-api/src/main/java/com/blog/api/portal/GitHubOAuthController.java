package com.blog.api.portal;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.system.dto.LoginResponse;
import com.blog.system.service.GitHubOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitHub OAuth 登录接口
 */
@Tag(name = "用户端-GitHub OAuth", description = "GitHub 第三方登录")
@RestController
@RequestMapping("/api/portal/oauth/github")
@RequiredArgsConstructor
public class GitHubOAuthController {

    private final GitHubOAuthService gitHubOAuthService;
    private static final String OAUTH_STATE_COOKIE = "weblog_github_oauth_state";
    private static final Duration OAUTH_STATE_COOKIE_TTL = Duration.ofMinutes(5);

    @Operation(summary = "获取 GitHub 授权 URL", description = "生成 GitHub OAuth 授权链接，前端跳转到该链接进行授权")
    @GetMapping("/authorize")
    @RateLimit(key = "github-oauth-authorize", capacity = 20, seconds = 60)
    public Result<String> authorize(@Parameter(description = "OAuth 回调地址") @RequestParam String redirectUri,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        String authUrl = gitHubOAuthService.getAuthorizationUrl(redirectUri);
        String state = extractStateFromUrl(authUrl);
        if (state == null || state.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "生成 OAuth 授权地址失败");
        }

        writeStateCookie(response, request, state, OAUTH_STATE_COOKIE_TTL);
        return Result.success(authUrl);
    }

    @Operation(summary = "GitHub OAuth 回调", description = "GitHub 授权后回调，用 code 换取 token 并登录")
    @PostMapping("/callback")
    @RateLimit(key = "github-oauth-callback", capacity = 20, seconds = 60)
    @AuditLog(module = "认证", operation = "LOGIN", description = "GitHub OAuth 登录")
    public Result<LoginResponse> callback(@RequestParam String code,
                                          @RequestParam String state,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        verifyStateCookie(request, state);
        clearStateCookie(response, request);

        String clientIp = IpUtil.getClientIp(request);
        LoginResponse loginResponse = gitHubOAuthService.handleCallback(code, state, clientIp);
        return Result.success(loginResponse);
    }

    private void verifyStateCookie(HttpServletRequest request, String state) {
        String stateInCookie = readCookie(request, OAUTH_STATE_COOKIE);
        if (stateInCookie == null || !stateInCookie.equals(state)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "OAuth state 校验失败");
        }
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

    private String extractStateFromUrl(String authUrl) {
        try {
            URI uri = URI.create(authUrl);
            String query = uri.getQuery();
            if (query == null || query.isBlank()) {
                return null;
            }

            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "state".equals(kv[0])) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeStateCookie(HttpServletResponse response,
                                  HttpServletRequest request,
                                  String state,
                                  Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(ttl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearStateCookie(HttpServletResponse response, HttpServletRequest request) {
        ResponseCookie cookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto);
    }
}
