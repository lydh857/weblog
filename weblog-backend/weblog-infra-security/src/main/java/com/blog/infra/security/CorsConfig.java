package com.blog.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS 跨域配置
 * 通过配置文件控制允许的来源，默认只允许本地开发端口
 */
@Configuration
public class CorsConfig {

    @Value("${blog.security.allowed-origins:${blog.cors.allowed-origins:http://localhost:3000,http://localhost:3001}}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 从配置读取允许的来源（逗号分隔）
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (origins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("CORS 配置不允许使用通配符 '*'（当前已启用 credentials）");
        }

        config.setAllowedOrigins(origins);
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-Captcha-Token"
        ));
        config.setExposedHeaders(List.of("Authorization"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // 静态资源也需要 CORS（前端裁剪组件 canvas 需要跨域读取图片像素）
        source.registerCorsConfiguration("/uploads/**", config);
        return new CorsFilter(source);
    }
}
