package com.blog.interaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;

/**
 * 访问控制服务
 * - 未登录用户每日限读3篇文章
 * - 基于设备指纹（UA+IP hash）识别用户
 * - 支持滑块验证解锁额外阅读
 * - 支持豁免页面配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final StringRedisTemplate redisTemplate;

    /** 每日免费阅读篇数 */
    private static final int DAILY_FREE_LIMIT = 3;
    /** 滑块验证解锁额外篇数 */
    private static final int UNLOCK_EXTRA = 5;
    /** Redis key前缀: 设备每日阅读文章集合 */
    private static final String KEY_DAILY_READ = "access:daily:";
    /** Redis key前缀: 滑块验证解锁标记 */
    private static final String KEY_UNLOCK = "access:unlock:";

    /**
     * 检查设备是否可以阅读该文章
     * @param fingerprint 设备指纹
     * @param postId 文章ID
     * @return true=可以阅读, false=已达限制
     */
    public boolean canRead(String fingerprint, Long postId) {
        String key = dailyKey(fingerprint);
        // 如果该文章已经在今日阅读列表中，允许重复访问
        Boolean isMember = redisTemplate.opsForSet().isMember(key, postId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            return true;
        }
        // 检查阅读数量
        Long readCount = redisTemplate.opsForSet().size(key);
        long limit = getLimit(fingerprint);
        return readCount == null || readCount < limit;
    }

    /**
     * 记录阅读（将文章加入今日阅读集合）
     */
    public void recordRead(String fingerprint, Long postId) {
        String key = dailyKey(fingerprint);
        redisTemplate.opsForSet().add(key, postId.toString());
        // 设置过期时间到明天0点
        redisTemplate.expire(key, Duration.ofDays(1));
    }

    /**
     * 获取今日已阅读数量
     */
    public long getReadCount(String fingerprint) {
        Long size = redisTemplate.opsForSet().size(dailyKey(fingerprint));
        return size != null ? size : 0;
    }

    /**
     * 获取当前限制数量
     */
    public long getLimit(String fingerprint) {
        String unlockKey = KEY_UNLOCK + LocalDate.now() + ":" + fingerprint;
        Boolean unlocked = redisTemplate.hasKey(unlockKey);
        return DAILY_FREE_LIMIT + (Boolean.TRUE.equals(unlocked) ? UNLOCK_EXTRA : 0);
    }

    /**
     * 获取今日已阅读的文章ID集合
     */
    public Set<String> getReadPostIds(String fingerprint) {
        return redisTemplate.opsForSet().members(dailyKey(fingerprint));
    }

    /**
     * 滑块验证通过后解锁额外阅读
     */
    public void unlock(String fingerprint) {
        String unlockKey = KEY_UNLOCK + LocalDate.now() + ":" + fingerprint;
        redisTemplate.opsForValue().set(unlockKey, "1", Duration.ofDays(1));
        log.info("设备解锁额外阅读: fingerprint={}", fingerprint.substring(0, 8) + "...");
    }

    /**
     * 检查是否已解锁
     */
    public boolean isUnlocked(String fingerprint) {
        String unlockKey = KEY_UNLOCK + LocalDate.now() + ":" + fingerprint;
        return Boolean.TRUE.equals(redisTemplate.hasKey(unlockKey));
    }

    /**
     * 生成设备指纹（服务端计算）
     * 基于 UserAgent + IP 生成 SHA-256 hash
     */
    public static String generateFingerprint(String userAgent, String ip) {
        String raw = (userAgent != null ? userAgent : "") + "|" + (ip != null ? ip : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在所有 JVM 中都可用，不会到这里
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    private String dailyKey(String fingerprint) {
        return KEY_DAILY_READ + LocalDate.now() + ":" + fingerprint;
    }
}
