package com.blog.infra.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作封装
 */
@Service
public class RedisService {

    private static final DefaultRedisScript<Long> DELETE_KEY_SCRIPT;
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

    static {
        DELETE_KEY_SCRIPT = new DefaultRedisScript<>();
        DELETE_KEY_SCRIPT.setScriptText("return redis.call('del', KEYS[1])");
        DELETE_KEY_SCRIPT.setResultType(Long.class);

        GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>();
        GET_AND_DELETE_SCRIPT.setScriptText("local value = redis.call('get', KEYS[1]); if value then redis.call('del', KEYS[1]); end; return value");
        GET_AND_DELETE_SCRIPT.setResultType(String.class);

        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
        RELEASE_LOCK_SCRIPT.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ========== String 操作 ==========

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 原子消费 key（存在则删除并返回 true，不存在返回 false）
     */
    public boolean consumeKey(String key) {
        Long deleted = stringRedisTemplate.execute(DELETE_KEY_SCRIPT, Collections.singletonList(key));
        return deleted != null && deleted > 0;
    }

    /**
     * 原子读取并删除 key（一次性令牌消费）
     */
    public String getAndDelete(String key) {
        return stringRedisTemplate.execute(GET_AND_DELETE_SCRIPT, Collections.singletonList(key));
    }

    /**
     * 分布式锁安全释放：仅当 value 匹配时删除 key
     */
    public boolean releaseLock(String key, String lockValue) {
        if (key == null || lockValue == null) {
            return false;
        }
        Long deleted = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(key), lockValue);
        return deleted != null && deleted > 0;
    }

    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return stringRedisTemplate.expire(key, timeout, unit);
    }

    /**
     * 自增并设置过期时间（用于限流计数）
     */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrement(String key) {
        return stringRedisTemplate.opsForValue().decrement(key);
    }

    // ========== 限流辅助 ==========

    /**
     * 滑动窗口计数器：自增并在首次设置过期时间
     * @return 当前窗口内的计数
     */
    public long incrementWithExpire(String key, long expireSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }
        return count != null ? count : 0;
    }

    /**
     * 获取 key 的剩余过期时间
     */
    public Long getTtl(String key, TimeUnit unit) {
        return stringRedisTemplate.getExpire(key, unit);
    }
}
