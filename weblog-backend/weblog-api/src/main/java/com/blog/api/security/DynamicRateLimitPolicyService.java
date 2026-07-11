package com.blog.api.security;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.infra.redis.RedisService;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class DynamicRateLimitPolicyService {

    private static final String REDIS_PREFIX = "rate:dynamic:";

    private final SystemConfigService systemConfigService;
    private final RedisService redisService;

    public void enforcePerIp(String scene,
                             String configKey,
                             int defaultCapacity,
                             int minCapacity,
                             int maxCapacity,
                             int windowSeconds,
                             String clientIp,
                             String message) {
        int capacity = resolveCapacity(configKey, defaultCapacity, minCapacity, maxCapacity);
        String safeScene = (scene == null || scene.isBlank()) ? "default" : scene.trim();
        String safeIp = resolveClientIp(clientIp);
        String key = REDIS_PREFIX + safeScene + ":" + safeIp;
        long count = redisService.incrementWithExpire(key, Math.max(windowSeconds, 1));
        if (count > capacity) {
            throw new BusinessException(ResultCode.RATE_LIMIT, message);
        }
    }

    private int resolveCapacity(String configKey, int defaultCapacity, int minCapacity, int maxCapacity) {
        int configured = systemConfigService.getIntValue(configKey, defaultCapacity);
        if (configured < minCapacity || configured > maxCapacity) {
            return defaultCapacity;
        }
        return configured;
    }

    private String resolveClientIp(String clientIp) {
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        return IpUtil.getClientIp(attrs.getRequest());
    }
}
