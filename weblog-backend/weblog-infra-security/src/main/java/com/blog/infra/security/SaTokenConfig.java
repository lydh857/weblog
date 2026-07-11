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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SaTokenConfig.class);

    public SaTokenConfig(cn.dev33.satoken.config.SaTokenConfig saTokenConfig) {
        String jwtSecret = saTokenConfig.getJwtSecretKey();
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
            log.error("====================================================================");
            log.error("[SECURITY ALERT] JWT Secret Key is empty or too weak (less than 32 chars)!");
            log.error("Please configure JWT_SECRET env variable in production environment.");
            
            // 自动生成高强度临时密钥，确保安全性
            String fallbackSecret = java.util.UUID.randomUUID().toString().replace("-", "") 
                                  + java.util.UUID.randomUUID().toString().replace("-", "");
            saTokenConfig.setJwtSecretKey(fallbackSecret);
            log.error("A random high-strength secure JWT Secret Key has been generated for this session.");
            log.error("====================================================================");
        }
    }

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
