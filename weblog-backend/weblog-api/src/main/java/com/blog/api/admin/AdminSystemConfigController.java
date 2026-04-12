package com.blog.api.admin;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.scheduler.RankingComputeScheduler;
import com.blog.api.scheduler.SecurityLogCleanupScheduler;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import com.blog.system.entity.SystemConfig;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端 - 系统配置
 */
@Tag(name = "管理端-系统配置", description = "系统配置查询与更新")
@RestController
@RequestMapping("/api/admin/system-config")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private static final String RATE_LIMIT_AUTO_BLOCK_KEY_PREFIXES_KEY = "rate_limit_auto_block_key_prefixes";
    private static final String RATE_LIMIT_AUTO_BLOCK_ENABLED_KEY = "rate_limit_auto_block_enabled";
    private static final String RATE_LIMIT_AUTO_BLOCK_THRESHOLD_KEY = "rate_limit_auto_block_threshold";
    private static final String RATE_LIMIT_AUTO_BLOCK_WINDOW_MINUTES_KEY = "rate_limit_auto_block_window_minutes";
    private static final String RATE_LIMIT_AUTO_BLOCK_MINUTES_KEY = "rate_limit_auto_block_minutes";
    private static final Set<String> AUTO_BLOCK_ALLOWED_KEY_PREFIXES = Set.of(
            "register", "sendCode", "checkEmail", "forgotPassword", "captchaGenerate", "captchaVerify",
            "comment-create", "comment-delete", "comment-batch-delete", "comment-like-toggle", "comment-like-state",
            "portal-upload-image", "ad-apply", "friend-link-apply", "friend-link-update",
            "access-read", "access-unlock",
            "interaction-like-toggle", "interaction-like-state",
            "interaction-favorite-toggle", "interaction-favorite-state", "interaction-favorite-batch",
            "user-bind-email", "user-change-email", "user-set-password", "user-reset-password",
            "ai-chat", "ai-writing", "ai-meta", "admin-login",
            "admin-post-delete", "admin-post-permanent-delete",
            "admin-topic-delete", "admin-topic-permanent-delete",
            "admin-media-delete", "admin-media-cleanup",
            "admin-user-status-update", "admin-user-reset-password",
            "admin-ad-status-update", "admin-ad-delete", "admin-ad-permanent-delete",
            "admin-ad-apply-switch", "admin-ad-price-rules", "admin-ad-pit-update",
            "admin-friend-link-status-update", "admin-friend-link-delete",
            "admin-announcement-status-update", "admin-announcement-delete"
    );
    private static final Map<String, IntRangeRule> DYNAMIC_RATE_LIMIT_RULES = Map.ofEntries(
            Map.entry("register_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("login_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("admin_login_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("comment_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("send_code_rate_limit", new IntRangeRule(1, 60, 3)),
            Map.entry("check_email_rate_limit", new IntRangeRule(1, 60, 10)),
            Map.entry("forgot_password_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("captcha_generate_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("captcha_verify_rate_limit", new IntRangeRule(1, 180, 20)),
            Map.entry("upload_rate_limit", new IntRangeRule(1, 300, 20)),
            Map.entry("ad_apply_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("friend_link_apply_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("friend_link_update_rate_limit", new IntRangeRule(1, 60, 10)),
            Map.entry("access_read_rate_limit", new IntRangeRule(1, 240, 120)),
            Map.entry("access_unlock_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("interaction_like_toggle_rate_limit", new IntRangeRule(1, 120, 60)),
            Map.entry("interaction_like_state_rate_limit", new IntRangeRule(1, 180, 90)),
            Map.entry("interaction_favorite_toggle_rate_limit", new IntRangeRule(1, 120, 60)),
            Map.entry("interaction_favorite_state_rate_limit", new IntRangeRule(1, 180, 90)),
            Map.entry("interaction_favorite_batch_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("comment_delete_rate_limit", new IntRangeRule(1, 120, 30)),
            Map.entry("comment_batch_delete_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("comment_like_toggle_rate_limit", new IntRangeRule(1, 120, 60)),
            Map.entry("comment_like_state_rate_limit", new IntRangeRule(1, 180, 90)),
            Map.entry("ai_writing_rate_limit", new IntRangeRule(1, 300, 20)),
            Map.entry("ai_chat_rate_limit", new IntRangeRule(1, 300, 30)),
            Map.entry("ai_meta_rate_limit", new IntRangeRule(1, 300, 20)),
            Map.entry("admin_revoke_token_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_revoke_all_tokens_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("system_config_batch_update_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("ranking_refresh_rate_limit", new IntRangeRule(1, 60, 5)),
            Map.entry("admin_post_delete_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_post_permanent_delete_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("admin_topic_delete_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_topic_permanent_delete_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("admin_media_delete_rate_limit", new IntRangeRule(1, 120, 30)),
            Map.entry("admin_media_cleanup_rate_limit", new IntRangeRule(1, 120, 5)),
            Map.entry("admin_user_status_update_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_user_reset_password_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("admin_ad_status_update_rate_limit", new IntRangeRule(1, 120, 30)),
            Map.entry("admin_ad_delete_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_ad_permanent_delete_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("admin_ad_apply_switch_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_ad_price_rules_rate_limit", new IntRangeRule(1, 120, 10)),
            Map.entry("admin_ad_pit_update_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_friend_link_status_update_rate_limit", new IntRangeRule(1, 120, 30)),
            Map.entry("admin_friend_link_delete_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("admin_announcement_status_update_rate_limit", new IntRangeRule(1, 120, 30)),
            Map.entry("admin_announcement_delete_rate_limit", new IntRangeRule(1, 120, 20)),
            Map.entry("user_bind_email_rate_limit", new IntRangeRule(1, 60, 8)),
            Map.entry("user_change_email_rate_limit", new IntRangeRule(1, 60, 8)),
            Map.entry("user_set_password_rate_limit", new IntRangeRule(1, 60, 8)),
            Map.entry("user_reset_password_rate_limit", new IntRangeRule(1, 60, 8)),
            Map.entry(RATE_LIMIT_AUTO_BLOCK_THRESHOLD_KEY, new IntRangeRule(1, 500, 20)),
            Map.entry(RATE_LIMIT_AUTO_BLOCK_WINDOW_MINUTES_KEY, new IntRangeRule(1, 180, 10)),
            Map.entry(RATE_LIMIT_AUTO_BLOCK_MINUTES_KEY, new IntRangeRule(1, 43200, 60))
    );
    private static final int MAX_STEP_DELTA = 100;
    private static final int MAX_STEP_MULTIPLIER = 3;
    private static final int MAX_AUDIT_SUMMARY_LENGTH = 700;
    private static final String SYSTEM_CONFIG_BATCH_UPDATE_RATE_LIMIT_KEY = "system_config_batch_update_rate_limit";
    private static final String RANKING_REFRESH_RATE_LIMIT_KEY = "ranking_refresh_rate_limit";

    private final SystemConfigService systemConfigService;
    private final RankingComputeScheduler rankingComputeScheduler;
    private final SecurityLogCleanupScheduler securityLogCleanupScheduler;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    @Operation(summary = "获取所有系统配置")
    @GetMapping
    public Result<List<SystemConfig>> list() {
        return Result.success(systemConfigService.listAllForAdminView());
    }

    @Operation(summary = "批量更新系统配置")
    @PutMapping
    @RateLimit(key = "admin-system-config-batch-update", capacity = 120, seconds = 60)
    @AuditLog(module = "系统配置", operation = "UPDATE", description = "批量更新系统配置")
    public Result<Void> batchUpdate(@RequestBody Map<String, String> configs, HttpServletRequest request) {
        if (configs == null || configs.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "配置项不能为空");
        }
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-system-config-batch-update",
                SYSTEM_CONFIG_BATCH_UPDATE_RATE_LIMIT_KEY,
                20,
                1,
                120,
                60,
                null,
                "系统配置保存过于频繁，请稍后再试"
        );
        validateAutoBlockKeyPrefixes(configs);
        validateDynamicRateLimitRules(configs);
        validateDynamicRateLimitStepChange(configs);
        request.setAttribute(AuditLog.AUDIT_APPEND_DESCRIPTION_ATTR, buildAuditSummary(configs));
        systemConfigService.batchUpdate(configs);
        return Result.success();
    }

    private void validateDynamicRateLimitRules(Map<String, String> configs) {
        for (Map.Entry<String, IntRangeRule> entry : DYNAMIC_RATE_LIMIT_RULES.entrySet()) {
            String key = entry.getKey();
            if (!configs.containsKey(key)) {
                continue;
            }
            int value = parseIntConfig(configs.get(key), key);
            IntRangeRule rule = entry.getValue();
            if (value < rule.min() || value > rule.max()) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        String.format("%s 超出允许范围[%d,%d]", key, rule.min(), rule.max()));
            }
        }
        if (configs.containsKey(RATE_LIMIT_AUTO_BLOCK_ENABLED_KEY)) {
            String enabledValue = configs.get(RATE_LIMIT_AUTO_BLOCK_ENABLED_KEY);
            if (!"true".equalsIgnoreCase(enabledValue) && !"false".equalsIgnoreCase(enabledValue)) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        RATE_LIMIT_AUTO_BLOCK_ENABLED_KEY + " 仅允许 true 或 false");
            }
        }
    }

    private void validateDynamicRateLimitStepChange(Map<String, String> configs) {
        for (Map.Entry<String, IntRangeRule> entry : DYNAMIC_RATE_LIMIT_RULES.entrySet()) {
            String key = entry.getKey();
            if (!configs.containsKey(key)) {
                continue;
            }

            int newValue = parseIntConfig(configs.get(key), key);
            int oldValue = parseIntConfigOrDefault(systemConfigService.getValue(key), entry.getValue().defaultValue());
            if (oldValue <= 0 || newValue == oldValue) {
                continue;
            }

            int diff = Math.abs(newValue - oldValue);
            if (diff > MAX_STEP_DELTA) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        String.format("%s 单次调整过大，最多允许变化 %d", key, MAX_STEP_DELTA));
            }

            if (newValue > oldValue) {
                if ((long) newValue > (long) oldValue * MAX_STEP_MULTIPLIER) {
                    throw new BusinessException(ResultCode.PARAM_INVALID,
                            String.format("%s 单次上调倍率不能超过 %dx", key, MAX_STEP_MULTIPLIER));
                }
            } else if ((long) oldValue > (long) newValue * MAX_STEP_MULTIPLIER) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        String.format("%s 单次下调倍率不能超过 %dx", key, MAX_STEP_MULTIPLIER));
            }
        }
    }

    private String buildAuditSummary(Map<String, String> configs) {
        Set<String> trackedKeys = new HashSet<>(DYNAMIC_RATE_LIMIT_RULES.keySet());
        trackedKeys.add(RATE_LIMIT_AUTO_BLOCK_ENABLED_KEY);
        trackedKeys.add(RATE_LIMIT_AUTO_BLOCK_KEY_PREFIXES_KEY);

        List<String> changeItems = new ArrayList<>();
        configs.keySet().stream()
                .filter(trackedKeys::contains)
                .sorted(Comparator.naturalOrder())
                .forEach(key -> {
                    String oldValue = systemConfigService.getValue(key);
                    String newValue = configs.get(key);
                    if (Objects.equals(normalizeNullable(oldValue), normalizeNullable(newValue))) {
                        return;
                    }
                    changeItems.add(key + ": " + maskIfSensitive(key, oldValue) + " -> " + maskIfSensitive(key, newValue));
                });

        if (changeItems.isEmpty()) {
            return "限流相关配置无实际变化";
        }
        String summary = "限流相关配置变更: " + String.join("; ", changeItems);
        if (summary.length() > MAX_AUDIT_SUMMARY_LENGTH) {
            return summary.substring(0, MAX_AUDIT_SUMMARY_LENGTH) + "...";
        }
        return summary;
    }

    private void validateAutoBlockKeyPrefixes(Map<String, String> configs) {
        if (configs == null || !configs.containsKey(RATE_LIMIT_AUTO_BLOCK_KEY_PREFIXES_KEY)) {
            return;
        }
        String raw = configs.get(RATE_LIMIT_AUTO_BLOCK_KEY_PREFIXES_KEY);
        Set<String> parsed = Set.of();
        if (raw != null && !raw.isBlank()) {
            parsed = java.util.Arrays.stream(raw.split(","))
                    .map(item -> item == null ? "" : item.trim())
                    .filter(item -> !item.isEmpty())
                    .collect(Collectors.toSet());
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "限流自动封禁适用接口不能为空");
        }

        Set<String> invalid = parsed.stream()
                .filter(item -> !AUTO_BLOCK_ALLOWED_KEY_PREFIXES.contains(item))
                .collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "存在非法限流 key 前缀: " + String.join(",", invalid));
        }
    }

    private int parseIntConfig(String rawValue, String key) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, key + " 不能为空");
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, key + " 必须为整数");
        }
    }

    private int parseIntConfigOrDefault(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String maskIfSensitive(String key, String value) {
        if (key != null && key.toLowerCase().contains("password")) {
            return "******";
        }
        String text = value == null ? "" : value;
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    private record IntRangeRule(int min, int max, int defaultValue) {
    }

    @Operation(summary = "手动刷新排行榜")
    @PostMapping("/refresh-ranking")
    @RateLimit(key = "admin-refresh-ranking", capacity = 120, seconds = 60)
    @AuditLog(module = "系统配置", operation = "UPDATE", description = "手动刷新排行榜")
    public Result<Void> refreshRanking() {
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-refresh-ranking",
                RANKING_REFRESH_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                null,
                "排行榜刷新过于频繁，请稍后再试"
        );
        rankingComputeScheduler.computeRankingsNow();
        return Result.success();
    }

    @Operation(summary = "手动清理安全日志", description = "立即执行登录日志和审计日志清理")
    @PostMapping("/cleanup-security-logs")
    @RateLimit(key = "admin-cleanup-security-logs", capacity = 5, seconds = 60)
    @AuditLog(module = "系统配置", operation = "CLEANUP", description = "手动清理安全日志")
    public Result<SecurityLogCleanupScheduler.CleanupResult> cleanupSecurityLogs() {
        return Result.success(securityLogCleanupScheduler.cleanupNow());
    }

    @Operation(summary = "手动清理登录日志", description = "仅清理超出保留期的登录日志")
    @PostMapping("/cleanup-login-logs")
    @RateLimit(key = "admin-cleanup-login-logs", capacity = 5, seconds = 60)
    @AuditLog(module = "系统配置", operation = "CLEANUP", description = "手动清理登录日志")
    public Result<SecurityLogCleanupScheduler.DeletedResult> cleanupLoginLogs() {
        int deleted = securityLogCleanupScheduler.cleanupLoginLogsNow();
        return Result.success(new SecurityLogCleanupScheduler.DeletedResult(deleted));
    }

    @Operation(summary = "手动清理审计日志", description = "仅清理超出保留期的审计日志")
    @PostMapping("/cleanup-audit-logs")
    @RateLimit(key = "admin-cleanup-audit-logs", capacity = 5, seconds = 60)
    @AuditLog(module = "系统配置", operation = "CLEANUP", description = "手动清理审计日志")
    public Result<SecurityLogCleanupScheduler.DeletedResult> cleanupAuditLogs() {
        int deleted = securityLogCleanupScheduler.cleanupAuditLogsNow();
        return Result.success(new SecurityLogCleanupScheduler.DeletedResult(deleted));
    }
}
