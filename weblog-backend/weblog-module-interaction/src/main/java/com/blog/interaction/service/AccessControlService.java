package com.blog.interaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 访问控制服务
 * - 未登录用户每日限读3篇文章
 * - 基于设备指纹（签名 deviceId + IP hash）识别用户
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
    /** 单设备每日最多记录的唯一文章数（防刷写放大） */
    private static final int DAILY_TRACKED_POSTS_CAP = 200;
    /** Redis key前缀: 设备每日阅读文章集合 */
    private static final String KEY_DAILY_READ = "access:daily:";
    /** Redis key前缀: 滑块验证解锁标记 */
    private static final String KEY_UNLOCK = "access:unlock:";
    /** 清理时保留最近天数（当天 + 昨天） */
    private static final int ACCESS_KEY_KEEP_DAYS = 2;

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

        Long currentSize = redisTemplate.opsForSet().size(key);
        if (currentSize != null && currentSize >= DAILY_TRACKED_POSTS_CAP) {
            log.warn("阅读记录已达每日上限，忽略写入: fingerprint={}, postId={}", fingerprint, postId);
            return;
        }

        redisTemplate.opsForSet().add(key, postId.toString());
        // 过期时间对齐到次日 0 点
        redisTemplate.expire(key, secondsUntilNextDay(), TimeUnit.SECONDS);
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
        log.debug("设备已解锁额外阅读");
    }

    /**
     * 检查是否已解锁
     */
    public boolean isUnlocked(String fingerprint) {
        String unlockKey = KEY_UNLOCK + LocalDate.now() + ":" + fingerprint;
        return Boolean.TRUE.equals(redisTemplate.hasKey(unlockKey));
    }

    /**
     * 每天凌晨清理历史访问控制键（TTL 兜底）
     */
    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanupLegacyAccessKeys() {
        int removedDaily = cleanupKeysByPrefix(KEY_DAILY_READ);
        int removedUnlock = cleanupKeysByPrefix(KEY_UNLOCK);
        int removedTotal = removedDaily + removedUnlock;
        if (removedTotal > 0) {
            log.info("访问控制历史键清理完成: daily={}, unlock={}, total={}", removedDaily, removedUnlock, removedTotal);
        }
    }

    /**
     * 生成设备指纹（服务端计算）
     * 基于受签名保护的 deviceId + IP 生成 SHA-256 hash
     */
    public static String generateFingerprint(String deviceId, String ip) {
        String raw = (deviceId != null ? deviceId : "") + "|" + (ip != null ? ip : "");
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

    private int cleanupKeysByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        LocalDate thresholdDate = LocalDate.now().minusDays(ACCESS_KEY_KEEP_DAYS);
        int removed = 0;
        for (String key : keys) {
            LocalDate keyDate = parseDateFromAccessKey(key, prefix);
            if (keyDate != null && keyDate.isBefore(thresholdDate)) {
                Boolean deleted = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(deleted)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private LocalDate parseDateFromAccessKey(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return null;
        }

        int dateStart = prefix.length();
        int dateEnd = key.indexOf(':', dateStart);
        if (dateEnd <= dateStart) {
            return null;
        }

        try {
            return LocalDate.parse(key.substring(dateStart, dateEnd));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private long secondsUntilNextDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        long seconds = Duration.between(now, tomorrowStart).getSeconds();
        return Math.max(seconds, 60L);
    }
}
