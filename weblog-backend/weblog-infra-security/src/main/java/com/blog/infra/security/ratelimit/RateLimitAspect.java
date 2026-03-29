package com.blog.infra.security.ratelimit;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面
 * 基于 Bucket4j 令牌桶算法实现接口级限流
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Environment environment;

    @Value("${blog.security.dev-bypass-enabled:false}")
    private boolean devBypassEnabled;

    /**
     * 本地令牌桶缓存（单机部署足够，集群部署需改为 Redis）
     * 定时清理过期条目，防止内存无限增长
     */
    private final Map<String, BucketEntry> bucketCache = new ConcurrentHashMap<>();

    /** 缓存条目最大存活时间（毫秒） */
    private static final long ENTRY_TTL_MS = 30 * 60 * 1000L; // 30分钟
    /** 缓存最大条目数 */
    private static final int MAX_CACHE_SIZE = 10000;

    /** 本地开发 IP 白名单，跳过限流 */
    private static final java.util.Set<String> LOCAL_IP_WHITELIST = java.util.Set.of(
            "127.0.0.1", "0:0:0:0:0:0:0:1", "::1", "localhost"
    );

    /**
     * 每10分钟清理过期的令牌桶条目
     */
    @Scheduled(fixedRate = 600000)
    public void evictExpiredBuckets() {
        long now = System.currentTimeMillis();
        int before = bucketCache.size();
        bucketCache.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > ENTRY_TTL_MS);
        int evicted = before - bucketCache.size();
        if (evicted > 0) {
            log.debug("限流缓存清理: 移除 {} 个过期条目，剩余 {}", evicted, bucketCache.size());
        }
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String clientIp = getClientIp();

        // 仅在开发/测试环境且显式开启时允许跳过
        if (rateLimit.perIp() && shouldBypassForDev(clientIp)) {
            return joinPoint.proceed();
        }

        String key = resolveKey(joinPoint, rateLimit, clientIp);

        // 优先使用 Redis 限流，支持多实例共享计数
        if (redisTemplate != null) {
            if (allowByRedisWindow(key, rateLimit.capacity(), rateLimit.seconds())) {
                return joinPoint.proceed();
            }
            log.warn("接口限流触发(redis): key={}", key);
            throw new BusinessException(ResultCode.RATE_LIMIT, rateLimit.message());
        }

        // Redis 不可用时降级到本地令牌桶
        if (!bucketCache.containsKey(key) && bucketCache.size() >= MAX_CACHE_SIZE) {
            log.warn("限流缓存已满（{}），拒绝新 key: {}", MAX_CACHE_SIZE, key);
            throw new BusinessException(ResultCode.RATE_LIMIT, rateLimit.message());
        }

        BucketEntry entry = bucketCache.computeIfAbsent(key,
                k -> new BucketEntry(createBucket(rateLimit.capacity(), rateLimit.seconds())));
        entry.lastAccessTime = System.currentTimeMillis();

        if (entry.bucket.tryConsume(1)) {
            return joinPoint.proceed();
        }

        log.warn("接口限流触发: key={}", key);
        throw new BusinessException(ResultCode.RATE_LIMIT, rateLimit.message());
    }

    private Bucket createBucket(int capacity, int seconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(seconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String clientIp) {
        String prefix = rateLimit.key().isEmpty()
                ? ((MethodSignature) joinPoint.getSignature()).getMethod().getName()
                : rateLimit.key();

        if (rateLimit.perIp()) {
            return prefix + ":" + clientIp;
        }
        return prefix;
    }

    private boolean allowByRedisWindow(String key, int capacity, int seconds) {
        long now = System.currentTimeMillis();
        long window = Math.max(1, seconds) * 1000L;
        long bucket = now / window;
        String redisKey = "rate:limit:" + key + ":" + bucket;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            return false;
        }
        if (count == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(seconds + 1L));
        }
        return count <= capacity;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        return IpUtil.getClientIp(attrs.getRequest());
    }

    private boolean shouldBypassForDev(String clientIp) {
        if (!devBypassEnabled || !LOCAL_IP_WHITELIST.contains(clientIp)) {
            return false;
        }

        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            return false;
        }

        for (String profile : profiles) {
            if ("dev".equalsIgnoreCase(profile)
                    || "test".equalsIgnoreCase(profile)
                    || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 令牌桶缓存条目，记录最后访问时间用于过期淘汰
     */
    private static class BucketEntry {
        final Bucket bucket;
        volatile long lastAccessTime;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
