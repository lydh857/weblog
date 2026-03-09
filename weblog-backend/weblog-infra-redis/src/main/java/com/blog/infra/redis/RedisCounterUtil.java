package com.blog.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Redis 计数器工具：安全递减，保证值不低于 0
 */
public final class RedisCounterUtil {

    private RedisCounterUtil() {}

    /**
     * Lua 脚本：当 key 存在且值 > 0 时递减，否则设为 0
     * 返回递减后的值
     */
    private static final DefaultRedisScript<Long> SAFE_DECR_SCRIPT = new DefaultRedisScript<>(
        "local v = redis.call('GET', KEYS[1]) " +
        "if v == false then redis.call('SET', KEYS[1], '0') return 0 end " +
        "local n = tonumber(v) " +
        "if n <= 0 then redis.call('SET', KEYS[1], '0') return 0 end " +
        "return redis.call('DECR', KEYS[1])",
        Long.class
    );

    /**
     * 安全递减：保证值不低于 0
     */
    public static long safeDecrement(StringRedisTemplate redisTemplate, String key) {
        Long result = redisTemplate.execute(SAFE_DECR_SCRIPT, List.of(key));
        return result != null ? result : 0;
    }
}
