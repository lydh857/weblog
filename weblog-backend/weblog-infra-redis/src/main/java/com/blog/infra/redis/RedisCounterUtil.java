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
     * Lua 脚本：按指定步长安全递减，保证值不低于 0
     */
    private static final DefaultRedisScript<Long> SAFE_DECR_BY_SCRIPT = new DefaultRedisScript<>(
        "local v = redis.call('GET', KEYS[1]) " +
        "if v == false then redis.call('SET', KEYS[1], '0') return 0 end " +
        "local n = tonumber(v) " +
        "if n == nil or n <= 0 then redis.call('SET', KEYS[1], '0') return 0 end " +
        "local d = tonumber(ARGV[1]) " +
        "if d == nil or d <= 0 then return n end " +
        "local next = n - d " +
        "if next < 0 then next = 0 end " +
        "redis.call('SET', KEYS[1], tostring(next)) " +
        "return next",
        Long.class
    );

    /**
     * 安全递减：保证值不低于 0
     */
    public static long safeDecrement(StringRedisTemplate redisTemplate, String key) {
        Long result = redisTemplate.execute(SAFE_DECR_SCRIPT, List.of(key));
        return result != null ? result : 0;
    }

    /**
     * 按步长安全递减：保证值不低于 0
     */
    public static long safeDecrementBy(StringRedisTemplate redisTemplate, String key, long delta) {
        if (delta <= 0) {
            return 0;
        }
        Long result = redisTemplate.execute(SAFE_DECR_BY_SCRIPT, List.of(key), String.valueOf(delta));
        return result != null ? result : 0;
    }
}
