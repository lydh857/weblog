package com.blog.api.security;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 上传配额服务：按用户/IP/全站执行次数与字节双重限制。
 */
@Service
@RequiredArgsConstructor
public class UploadGuardService {

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String DEFAULT_IP = "unknown";

    private static final DefaultRedisScript<Long> QUOTA_SCRIPT = new DefaultRedisScript<>(
            "local count = tonumber(redis.call('GET', KEYS[1]) or '0') " +
                    "local bytes = tonumber(redis.call('GET', KEYS[2]) or '0') " +
                    "local incCount = tonumber(ARGV[1]) " +
                    "local incBytes = tonumber(ARGV[2]) " +
                    "local maxCount = tonumber(ARGV[3]) " +
                    "local maxBytes = tonumber(ARGV[4]) " +
                    "local ttl = tonumber(ARGV[5]) " +
                    "if maxCount > 0 and (count + incCount) > maxCount then return 0 end " +
                    "if maxBytes > 0 and (bytes + incBytes) > maxBytes then return 0 end " +
                    "local newCount = redis.call('INCRBY', KEYS[1], incCount) " +
                    "local newBytes = redis.call('INCRBY', KEYS[2], incBytes) " +
                    "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ttl) end " +
                    "if redis.call('TTL', KEYS[2]) < 0 then redis.call('EXPIRE', KEYS[2], ttl) end " +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final UploadGuardProperties properties;

    public void consumeUpload(UploadScene scene, Long userId, String clientIp, long bytes) {
        if (!properties.isEnabled()) {
            return;
        }
        if (bytes <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件大小非法");
        }

        UploadGuardProperties.SceneLimit sceneLimit = resolveSceneLimit(scene);
        int dailyTtl = secondsToNextDay();
        int monthlyTtl = secondsToNextMonth();
        String day = LocalDate.now().format(DAY_FORMATTER);
        String month = YearMonth.now().format(MONTH_FORMATTER);

        if (userId != null) {
            consume(
                    "upload:guard:day:" + day + ":scene:" + scene.key() + ":user:" + userId,
                    1,
                    bytes,
                    sceneLimit.getDailyCountLimit(),
                    sceneLimit.getDailyBytesLimit(),
                    dailyTtl,
                    scene.limitExceededMessage());
        }

        consume(
                "upload:guard:day:" + day + ":ip:" + normalizeIp(clientIp),
                1,
                bytes,
                properties.getIpDaily().getDailyCountLimit(),
                properties.getIpDaily().getDailyBytesLimit(),
                dailyTtl,
                "上传过于频繁，请稍后再试");

        consume(
                "upload:guard:day:" + day + ":global",
                1,
                bytes,
                properties.getGlobalDaily().getDailyCountLimit(),
                properties.getGlobalDaily().getDailyBytesLimit(),
                dailyTtl,
                "站点上传流量已达当日上限，请明日再试");

        consume(
                "upload:guard:month:" + month + ":global",
                1,
                bytes,
                properties.getGlobalMonthly().getDailyCountLimit(),
                properties.getGlobalMonthly().getDailyBytesLimit(),
                monthlyTtl,
                "站点上传流量已达当月上限，请联系管理员");
    }

    public void consumeDirectSign(Long userId, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }

        int dailyTtl = secondsToNextDay();
        String day = LocalDate.now().format(DAY_FORMATTER);
        UploadGuardProperties.SceneLimit signLimit = properties.getDirectSign();

        if (userId != null) {
            consume(
                    "upload:guard:day:" + day + ":sign:user:" + userId,
                    1,
                    0,
                    signLimit.getDailyCountLimit(),
                    0,
                    dailyTtl,
                    "直传签名请求过于频繁，请稍后再试");
        }

        consume(
                "upload:guard:day:" + day + ":sign:ip:" + normalizeIp(clientIp),
                1,
                0,
                properties.getIpDaily().getDailyCountLimit(),
                0,
                dailyTtl,
                "直传签名请求过于频繁，请稍后再试");
    }

    private UploadGuardProperties.SceneLimit resolveSceneLimit(UploadScene scene) {
        return switch (scene) {
            case ADMIN_IMAGE -> properties.getAdminImage();
            case PORTAL_IMAGE -> properties.getPortalImage();
            case AVATAR_IMAGE -> properties.getAvatarImage();
        };
    }

    private void consume(String keyPrefix,
                         long incCount,
                         long incBytes,
                         long maxCount,
                         long maxBytes,
                         int ttlSeconds,
                         String exceededMessage) {
        if (maxCount <= 0 && maxBytes <= 0) {
            return;
        }

        String countKey = keyPrefix + ":count";
        String bytesKey = keyPrefix + ":bytes";

        Long allowed = redisTemplate.execute(
                QUOTA_SCRIPT,
                List.of(countKey, bytesKey),
                String.valueOf(incCount),
                String.valueOf(Math.max(incBytes, 0)),
                String.valueOf(maxCount),
                String.valueOf(maxBytes),
                String.valueOf(Math.max(ttlSeconds, 60))
        );

        if (allowed == null || allowed != 1L) {
            throw new BusinessException(ResultCode.RATE_LIMIT, exceededMessage);
        }
    }

    private int secondsToNextDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        return (int) Duration.between(now, tomorrow).getSeconds();
    }

    private int secondsToNextMonth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonth = YearMonth.now().plusMonths(1)
                .atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toLocalDateTime();
        return (int) Duration.between(now, nextMonth).getSeconds();
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return DEFAULT_IP;
        }
        return clientIp.replace(':', '_').replace('.', '_');
    }

    public enum UploadScene {
        ADMIN_IMAGE("admin-image", "管理端上传过于频繁，请稍后再试"),
        PORTAL_IMAGE("portal-image", "素材上传过于频繁，请稍后再试"),
        AVATAR_IMAGE("avatar-image", "头像上传过于频繁，请稍后再试");

        private final String key;
        private final String limitExceededMessage;

        UploadScene(String key, String limitExceededMessage) {
            this.key = key;
            this.limitExceededMessage = limitExceededMessage;
        }

        public String key() {
            return key;
        }

        public String limitExceededMessage() {
            return limitExceededMessage;
        }
    }
}
