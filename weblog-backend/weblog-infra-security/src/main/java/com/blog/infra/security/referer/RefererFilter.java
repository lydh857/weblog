package com.blog.infra.security.referer;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 请求来源验证过滤器
 * 验证请求的 Referer 和 Origin 头，防止 API 被恶意调用
 */
@Slf4j
@Component
public class RefererFilter implements Filter {

    @Value("${blog.security.referer-enabled:false}")
    private boolean refererEnabled;

    @Value("${blog.security.allowed-origins:}")
    private String allowedOrigins;

    private Set<String> originSet = Set.of();

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op: use @PostConstruct for configuration parsing
    }

    @PostConstruct
    public void initAllowedOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            originSet = Set.of();
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : allowedOrigins.split(",")) {
            String value = normalizeOrigin(raw);
            if (value != null) {
                normalized.add(value);
            }
        }
        originSet = normalized;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!refererEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();

        // 只对状态变更请求进行验证
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {

            String referer = httpRequest.getHeader("Referer");
            String origin = httpRequest.getHeader("Origin");

            if (!isAllowedOrigin(referer, origin)) {
                log.warn("请求来源验证失败: referer={}, origin={}, uri={}",
                        referer, origin, httpRequest.getRequestURI());
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"code\":403,\"message\":\"Invalid request origin\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String referer, String origin) {
        // 如果没有配置允许的来源，跳过验证
        if (originSet.isEmpty()) {
            return true;
        }

        String normalizedOrigin = normalizeOrigin(origin);
        if (normalizedOrigin != null) {
            return originSet.contains(normalizedOrigin);
        }

        String refererOrigin = extractOrigin(referer);
        if (refererOrigin != null) {
            return originSet.contains(refererOrigin);
        }

        return false;
    }

    private String extractOrigin(String referer) {
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            URI uri = new URI(referer.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(uri.getScheme().toLowerCase()).append("://").append(uri.getHost().toLowerCase());
            if (uri.getPort() != -1) {
                sb.append(":").append(uri.getPort());
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private String normalizeOrigin(String origin) {
        if (!StringUtils.hasText(origin)) {
            return null;
        }
        String value = origin.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        // Origin 有时直接为 scheme://host[:port]
        String fromOrigin = extractOrigin(value);
        if (fromOrigin != null) {
            return fromOrigin;
        }
        return null;
    }

    @Override
    public void destroy() {
    }
}
