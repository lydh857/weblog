package com.blog.api.config;

import com.blog.common.util.IpUtil;
import com.blog.system.service.SecurityRiskControlService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class IpBlacklistFilterConfig {

    private final SecurityRiskControlService securityRiskControlService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public OncePerRequestFilter ipBlacklistFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String uri = request.getRequestURI();
                return uri == null || !uri.startsWith("/api/");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                try {
                    String ip = IpUtil.getClientIp(request);
                    String scene = request.getMethod() + " " + request.getRequestURI();
                    String userAgent = request.getHeader("User-Agent");
                    securityRiskControlService.assertIpAllowed(ip, scene, userAgent);
                    filterChain.doFilter(request, response);
                } catch (Exception ex) {
                    addCorsHeadersIfNeeded(request, response);
                    handlerExceptionResolver.resolveException(request, response, null, ex);
                }
            }

            private void addCorsHeadersIfNeeded(HttpServletRequest request, HttpServletResponse response) {
                String origin = request.getHeader(HttpHeaders.ORIGIN);
                if (!StringUtils.hasText(origin)) {
                    return;
                }

                CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
                if (config == null) {
                    return;
                }

                String allowOrigin = config.checkOrigin(origin);
                if (!StringUtils.hasText(allowOrigin)) {
                    return;
                }

                if (!response.containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)) {
                    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
                }

                if (Boolean.TRUE.equals(config.getAllowCredentials())
                        && !response.containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)) {
                    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                }

                String vary = response.getHeader(HttpHeaders.VARY);
                if (!StringUtils.hasText(vary)) {
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
                } else if (!vary.contains(HttpHeaders.ORIGIN)) {
                    response.setHeader(HttpHeaders.VARY, vary + ", " + HttpHeaders.ORIGIN);
                }
            }
        };
    }
}
