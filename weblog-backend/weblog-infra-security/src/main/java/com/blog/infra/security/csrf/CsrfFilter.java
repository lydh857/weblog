package com.blog.infra.security.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF 防护过滤器
 *
 * 方案：SameSite Cookie 验证
 *
 * 1. GET/HEAD/OPTIONS：签发 CSRF Cookie
 * 2. POST/PUT/DELETE/PATCH：验证 Cookie 存在即可（浏览器自动发送同源 Cookie）
 *
 * 依赖 SameSite=Strict + HttpOnly Cookie，前端无需额外处理。
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {

    private static final String CSRF_TOKEN_COOKIE = "X-CSRF-TOKEN";
    private static final String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";
    private static final int TOKEN_LENGTH = 32;
    private static final int COOKIE_MAX_AGE = 3600; // 1小时
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String cookieToken = getCookieValue(request, CSRF_TOKEN_COOKIE);

        // GET/HEAD/OPTIONS：生成并设置 Cookie
        if (SAFE_METHODS.contains(method)) {
            ensureCsrfToken(request, response, cookieToken);
            filterChain.doFilter(request, response);
            return;
        }

        // POST/PUT/DELETE/PATCH：验证 Cookie 存在
        if (!validateCsrfToken(request, cookieToken)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"CSRF token validation failed\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 生成 CSRF Token 并设置 Cookie，若已有 token 则复用
     */
    private String ensureCsrfToken(HttpServletRequest request, HttpServletResponse response, String existingToken) {
        if (existingToken != null && !existingToken.isBlank()) {
            return existingToken;
        }

        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        boolean secure = request.isSecure();
        String cookieValue = buildCookieValue(token, secure);
        response.addHeader("Set-Cookie", cookieValue);
        return token;
    }

    /**
     * 构建 Cookie 值
     */
    private String buildCookieValue(String token, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSRF_TOKEN_COOKIE).append("=").append(token);
        sb.append("; Path=/");
        sb.append("; Max-Age=").append(COOKIE_MAX_AGE);
        sb.append("; SameSite=Strict");
        sb.append("; HttpOnly");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /**
     * 验证 CSRF Token（仅验证 Cookie，浏览器会自动发送）
     */
    private boolean validateCsrfToken(HttpServletRequest request, String cookieToken) {
        // Cookie 存在即通过验证（浏览器自动发送同源 Cookie）
        return cookieToken != null && !cookieToken.isBlank();
    }

    /**
     * 从 Cookie 中获取值
     */
    private String getCookieValue(HttpServletRequest request, String cookieName) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 排除不需要 CSRF 验证的路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        // OPTIONS 请求（CORS 预检）不需要 CSRF 验证
        if ("OPTIONS".equals(method)) {
            return true;
        }

        // 无状态公开接口不需要 CSRF 验证
        return path.startsWith("/api/portal/auth/login") ||
               path.startsWith("/api/portal/auth/register") ||
               path.startsWith("/api/portal/auth/login-by-code") ||
               path.startsWith("/api/portal/auth/send-code") ||
               path.startsWith("/api/portal/auth/check-email") ||
               path.startsWith("/api/portal/auth/forgot-password") ||
               path.startsWith("/api/portal/auth/refresh") ||
               path.startsWith("/api/portal/auth/remember-login") ||
               path.startsWith("/api/admin/auth/login") ||
               path.startsWith("/api/admin/auth/refresh") ||
               path.startsWith("/api/admin/auth/remember-login") ||
               path.startsWith("/doc.html") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/actuator") ||
               path.startsWith("/api/portal/public");
    }
}
