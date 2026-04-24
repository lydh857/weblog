package com.blog.api.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由拦截配置
 * - /api/admin/** 需要登录且必须是 admin 角色
 * - /api/admin/auth/login 与 /api/admin/auth/remember-login 放行
 * - /api/portal/** 公开访问
 */
@Configuration
public class SaTokenRouteConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    StpUtil.checkRole("admin");
                }))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        "/api/admin/auth/remember-login",
                        "/api/admin/crawler/**"
                );
    }
}
