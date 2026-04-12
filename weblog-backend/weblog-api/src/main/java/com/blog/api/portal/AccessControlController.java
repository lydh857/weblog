package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.api.portal.support.DeviceFingerprintService;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.interaction.service.AccessControlService;
import com.blog.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端 - 访问控制接口
 */
@Slf4j
@Tag(name = "用户端 - 访问控制", description = "阅读限制、滑块验证解锁")
@RestController
@RequestMapping("/api/portal/access")
@RequiredArgsConstructor
public class AccessControlController {

    private final AccessControlService accessControlService;
    private final CaptchaService captchaService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final PostMapper postMapper;
    private final SystemConfigService systemConfigService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String DAILY_READ_LIMIT_KEY = "daily_read_limit";
    private static final String ACCESS_READ_RATE_LIMIT_KEY = "access_read_rate_limit";
    private static final String ACCESS_UNLOCK_RATE_LIMIT_KEY = "access_unlock_rate_limit";

    @Operation(summary = "检查是否可以阅读文章")
    @GetMapping("/check/{postId}")
    @RateLimit(key = "access-check", capacity = 120, seconds = 60)
    public Result<Map<String, Object>> checkAccess(@PathVariable Long postId,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        validateReadablePost(postId);

        // 已登录用户不受限制
        if (StpUtil.isLogin()) {
            return Result.success(Map.of(
                    "allowed", true,
                    "readCount", 0,
                    "limit", 999,
                    "loggedIn", true));
        }

        String fingerprint = deviceFingerprintService.resolveFingerprint(request, response);
        long dailyReadLimit = getDailyReadLimit();

        boolean allowed = accessControlService.canRead(fingerprint, postId, dailyReadLimit);
        long readCount = accessControlService.getReadCount(fingerprint);
        long limit = accessControlService.getLimit(fingerprint, dailyReadLimit);
        boolean unlocked = accessControlService.isUnlocked(fingerprint);

        return Result.success(Map.of(
                "allowed", allowed,
                "readCount", readCount,
                "limit", limit,
                "unlocked", unlocked,
                "loggedIn", false));
    }

    @Operation(summary = "记录阅读（文章详情页调用）")
    @PostMapping("/read/{postId}")
    @RateLimit(key = "access-read", capacity = 120, seconds = 60)
    public Result<Void> recordRead(@PathVariable Long postId,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "access-read",
                ACCESS_READ_RATE_LIMIT_KEY,
                120,
                1,
                240,
                60,
                IpUtil.getClientIp(request),
                "阅读记录请求过于频繁，请稍后再试"
        );
        validateReadablePost(postId);

        if (!StpUtil.isLogin()) {
            String fingerprint = deviceFingerprintService.resolveFingerprint(request, response);
            accessControlService.recordRead(fingerprint, postId);
        }
        return Result.success();
    }

    @Operation(summary = "滑块验证通过后解锁额外阅读")
    @PostMapping("/unlock")
    @RateLimit(key = "access-unlock", capacity = 5, seconds = 60)
    public Result<Void> unlock(@RequestHeader(value = "X-Captcha-Token") String verifyToken,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "access-unlock",
                ACCESS_UNLOCK_RATE_LIMIT_KEY,
                5,
                1,
                60,
                60,
                IpUtil.getClientIp(request),
                "解锁请求过于频繁，请稍后再试"
        );
        String clientIp = IpUtil.getClientIp(request);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);

        String fingerprint = deviceFingerprintService.resolveFingerprint(request, response);
        accessControlService.unlock(fingerprint);
        return Result.success();
    }

    private void validateReadablePost(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文章ID不合法");
        }
        if (postMapper.existsReadableById(postId) <= 0) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND, "文章不存在或不可访问");
        }
    }

    private long getDailyReadLimit() {
        int defaultLimit = accessControlService.getDefaultDailyFreeLimit();
        int configured = systemConfigService.getIntValue(DAILY_READ_LIMIT_KEY, defaultLimit);
        return Math.max(configured, 0);
    }
}
