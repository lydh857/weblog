package com.blog.system.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.redis.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录安全策略服务（基于系统配置）
 */
@Service
@RequiredArgsConstructor
public class LoginSecurityPolicyService {

    private static final String LOGIN_MAX_ATTEMPTS_KEY = "login_max_attempts";
    private static final String LOGIN_LOCK_MINUTES_KEY = "login_lock_minutes";
    private static final String LOGIN_RATE_LIMIT_KEY = "login_rate_limit";
    private static final String LOGIN_RATE_LIMIT_REDIS_PREFIX = "auth:login:rate:";

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCK_MINUTES = 30;
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 5;

    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 20;
    private static final int MIN_LOCK_MINUTES = 1;
    private static final int MAX_LOCK_MINUTES = 1440;
    private static final int MIN_RATE_LIMIT = 1;
    private static final int MAX_RATE_LIMIT = 60;

    private final SystemConfigService systemConfigService;
    private final RedisService redisService;

    public int getMaxFailedAttempts() {
        int value = systemConfigService.getIntValue(LOGIN_MAX_ATTEMPTS_KEY, DEFAULT_MAX_ATTEMPTS);
        return clamp(value, MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS);
    }

    public int getLockMinutes() {
        int value = systemConfigService.getIntValue(LOGIN_LOCK_MINUTES_KEY, DEFAULT_LOCK_MINUTES);
        return clamp(value, MIN_LOCK_MINUTES, MAX_LOCK_MINUTES, DEFAULT_LOCK_MINUTES);
    }

    public int getLoginRateLimitPerMinute() {
        int value = systemConfigService.getIntValue(LOGIN_RATE_LIMIT_KEY, DEFAULT_RATE_LIMIT_PER_MINUTE);
        return clamp(value, MIN_RATE_LIMIT, MAX_RATE_LIMIT, DEFAULT_RATE_LIMIT_PER_MINUTE);
    }

    public void enforceLoginRateLimit(String scene, String clientIp) {
        int capacity = getLoginRateLimitPerMinute();
        String safeScene = (scene == null || scene.isBlank()) ? "login" : scene;
        String safeIp = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp;
        String key = LOGIN_RATE_LIMIT_REDIS_PREFIX + safeScene + ":" + safeIp;
        long count = redisService.incrementWithExpire(key, 60);
        if (count > capacity) {
            throw new BusinessException(ResultCode.RATE_LIMIT, "登录请求过于频繁，请稍后再试");
        }
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
