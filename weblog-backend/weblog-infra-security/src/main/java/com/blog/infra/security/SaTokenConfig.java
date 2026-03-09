package com.blog.infra.security;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 配置
 * 使用 Simple 模式的 JWT（Token 中只存 loginId，其余信息存 Redis）
 */
@Configuration
public class SaTokenConfig {

    /**
     * Sa-Token 整合 JWT（Simple 模式）
     * Simple 模式：JWT 只存 loginId，权限等信息仍走 Redis
     * 适合本项目：既有 JWT 的无状态优势，又能通过 Redis 实现踢人下线
     */
    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }
}
