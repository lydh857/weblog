package com.blog.infra.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 安全响应头配置
 * 在应用层添加安全头（Nginx 层也会配置，双重保障）
 */
@Configuration
public class SecurityHeaderConfig {

    @Value("${blog.security.hsts-enabled:false}")
    private boolean hstsEnabled;

    @Bean
    public Filter securityHeaderFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response,
                                 FilterChain chain) throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                // 防止内容类型嗅探
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");

                // 防止点击劫持
                httpResponse.setHeader("X-Frame-Options", "DENY");

                // XSS 防护（传统浏览器）
                httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

                // 引用者策略
                httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // 权限策略（限制功能 API）
                httpResponse.setHeader("Permissions-Policy",
                        "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

                // HSTS（仅在 HTTPS 时启用）
                if (hstsEnabled && httpRequest.isSecure()) {
                    httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                }

                // 移除服务器版本信息
                httpResponse.setHeader("Server", "Weblog");

                chain.doFilter(request, response);
            }
        };
    }
}
