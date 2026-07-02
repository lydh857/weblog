package com.blog.api.security;

import com.blog.infra.redis.RedisService;
import com.blog.infra.security.audit.SecurityEventAuditService;
import com.blog.infra.security.ratelimit.RateLimitAspect;
import com.blog.system.service.SecurityRiskControlService;
import com.blog.system.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 限流处罚服务：对高风险限流命中执行自动封禁
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitPenaltyService {

    private static final String AUTO_BLOCK_ENABLED_KEY = "rate_limit_auto_block_enabled";
    private static final String AUTO_BLOCK_PERMANENT_ENABLED_KEY = "rate_limit_auto_block_permanent_enabled";
    private static final String AUTO_BLOCK_THRESHOLD_KEY = "rate_limit_auto_block_threshold";
    private static final String AUTO_BLOCK_WINDOW_MINUTES_KEY = "rate_limit_auto_block_window_minutes";
    private static final String AUTO_BLOCK_MINUTES_KEY = "rate_limit_auto_block_minutes";
    private static final String AUTO_BLOCK_KEY_PREFIXES_KEY = "rate_limit_auto_block_key_prefixes";
    private static final String AUTO_BLOCK_COUNTER_REDIS_PREFIX = "security:rate-limit:strike:";

    private static final boolean DEFAULT_AUTO_BLOCK_ENABLED = false;
    private static final boolean DEFAULT_AUTO_BLOCK_PERMANENT_ENABLED = false;
    private static final int DEFAULT_THRESHOLD = 20;
    private static final int DEFAULT_WINDOW_MINUTES = 10;
    private static final int DEFAULT_BLOCK_MINUTES = 60;
    private static final int MIN_THRESHOLD = 1;
    private static final int MAX_THRESHOLD = 500;
    private static final int MIN_WINDOW_MINUTES = 1;
    private static final int MAX_WINDOW_MINUTES = 180;
    private static final int MIN_BLOCK_MINUTES = 1;
    private static final int MAX_BLOCK_MINUTES = 43200;
    private static final String DEFAULT_KEY_PREFIXES =
            "register,sendCode,checkEmail,forgotPassword,captchaGenerate,captchaVerify,comment-create,comment-delete,comment-batch-delete,comment-like-toggle,comment-like-state,portal-upload-image,ad-apply,friend-link-apply,friend-link-update,access-read,access-unlock,interaction-like-toggle,interaction-like-state,interaction-favorite-toggle,interaction-favorite-state,interaction-favorite-batch,user-bind-email,user-change-email,user-set-password,user-reset-password,ai-chat,ai-writing,ai-meta,admin-login,admin-post-delete,admin-post-permanent-delete,admin-topic-delete,admin-topic-permanent-delete,admin-media-delete,admin-media-cleanup,admin-user-status-update,admin-user-reset-password,admin-ad-status-update,admin-ad-delete,admin-ad-permanent-delete,admin-ad-apply-switch,admin-ad-price-rules,admin-ad-pit-update,admin-friend-link-status-update,admin-friend-link-delete,admin-announcement-status-update,admin-announcement-delete";

    private final SystemConfigService systemConfigService;
    private final RedisService redisService;
    private final SecurityRiskControlService securityRiskControlService;
    private final SecurityEventAuditService securityEventAuditService;

    public void handleRateLimitHit(HttpServletRequest request) {
        if (request == null || !isAutoBlockEnabled()) {
            return;
        }
        Object hit = request.getAttribute(RateLimitAspect.ATTR_RATE_LIMIT_HIT);
        if (!(hit instanceof Boolean) || !((Boolean) hit)) {
            return;
        }

        String keyPrefix = asText(request.getAttribute(RateLimitAspect.ATTR_RATE_LIMIT_KEY));
        String clientIp = asText(request.getAttribute(RateLimitAspect.ATTR_RATE_LIMIT_IP));
        if (!StringUtils.hasText(keyPrefix) || !StringUtils.hasText(clientIp)) {
            return;
        }
        if (!isProtectedKey(keyPrefix)) {
            return;
        }

        int threshold = clamp(systemConfigService.getIntValue(AUTO_BLOCK_THRESHOLD_KEY, DEFAULT_THRESHOLD),
                MIN_THRESHOLD, MAX_THRESHOLD, DEFAULT_THRESHOLD);
        int windowMinutes = clamp(systemConfigService.getIntValue(AUTO_BLOCK_WINDOW_MINUTES_KEY, DEFAULT_WINDOW_MINUTES),
                MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES, DEFAULT_WINDOW_MINUTES);
        boolean permanentBlockEnabled = isAutoBlockPermanentEnabled();
        int blockMinutes = clamp(systemConfigService.getIntValue(AUTO_BLOCK_MINUTES_KEY, DEFAULT_BLOCK_MINUTES),
                MIN_BLOCK_MINUTES, MAX_BLOCK_MINUTES, DEFAULT_BLOCK_MINUTES);
        long expireSeconds = windowMinutes * 60L;

        String counterKey = AUTO_BLOCK_COUNTER_REDIS_PREFIX + keyPrefix + ":" + clientIp;
        long count = redisService.incrementWithExpire(counterKey, expireSeconds);
        if (count < threshold) {
            return;
        }

        SecurityRiskControlService.IpBlockStatus status = securityRiskControlService.getIpBlockStatus(clientIp);
        if (status.blocked()) {
            return;
        }

        String reason = String.format("接口限流触发自动封禁（key=%s, window=%d分钟, 次数=%d, 阈值=%d）",
                keyPrefix, windowMinutes, count, threshold);
        SecurityRiskControlService.IpBlockStatus blocked = permanentBlockEnabled
                ? securityRiskControlService.blockIp(clientIp, null, true, reason, false)
                : securityRiskControlService.blockIp(clientIp, blockMinutes, reason, false);
        if (!blocked.blocked()) {
            return;
        }

        securityEventAuditService.recordEvent(
                "安全防护",
                "RATE_LIMIT_AUTO_BLOCK_IP",
                reason,
                null,
                null,
                clientIp,
                request.getHeader("User-Agent"),
                200,
                request.getMethod(),
                request.getRequestURI()
        );
        log.warn("接口限流自动封禁生效: keyPrefix={}, ip={}, blockMode={}, blockMinutes={}, remainingSeconds={}",
                keyPrefix, clientIp, permanentBlockEnabled ? "PERMANENT" : "DURATION", blockMinutes, blocked.remainingSeconds());
    }

    private boolean isAutoBlockEnabled() {
        String raw = systemConfigService.getValue(AUTO_BLOCK_ENABLED_KEY);
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_AUTO_BLOCK_ENABLED;
        }
        return "true".equalsIgnoreCase(raw.trim());
    }

    private boolean isAutoBlockPermanentEnabled() {
        String raw = systemConfigService.getValue(AUTO_BLOCK_PERMANENT_ENABLED_KEY);
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_AUTO_BLOCK_PERMANENT_ENABLED;
        }
        return "true".equalsIgnoreCase(raw.trim());
    }

    private boolean isProtectedKey(String keyPrefix) {
        String raw = systemConfigService.getValue(AUTO_BLOCK_KEY_PREFIXES_KEY);
        String source = StringUtils.hasText(raw) ? raw : DEFAULT_KEY_PREFIXES;
        Set<String> allowed = Arrays.stream(source.split(","))
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toSet());
        return allowed.contains(keyPrefix);
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
