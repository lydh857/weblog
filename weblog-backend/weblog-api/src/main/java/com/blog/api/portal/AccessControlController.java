package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.result.Result;
import com.blog.common.util.IpUtil;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.infra.security.ratelimit.RateLimit;
import com.blog.interaction.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

    @Operation(summary = "检查是否可以阅读文章")
    @GetMapping("/check/{postId}")
    public Result<Map<String, Object>> checkAccess(@PathVariable Long postId,
                                                    HttpServletRequest request) {
        // 已登录用户不受限制
        if (StpUtil.isLogin()) {
            return Result.success(Map.of(
                    "allowed", true,
                    "readCount", 0,
                    "limit", 999,
                    "loggedIn", true));
        }

        String fingerprint = getFingerprint(request);

        boolean allowed = accessControlService.canRead(fingerprint, postId);
        long readCount = accessControlService.getReadCount(fingerprint);
        long limit = accessControlService.getLimit(fingerprint);
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
                                    HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            String fingerprint = getFingerprint(request);
            accessControlService.recordRead(fingerprint, postId);
        }
        return Result.success();
    }

    @Operation(summary = "滑块验证通过后解锁额外阅读")
    @PostMapping("/unlock")
    @RateLimit(key = "access-unlock", capacity = 5, seconds = 60)
    public Result<Void> unlock(@RequestHeader(value = "X-Captcha-Token") String verifyToken,
                               HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        captchaService.validateVerifyTokenOrThrow(verifyToken, clientIp);

        String fingerprint = getFingerprint(request);
        accessControlService.unlock(fingerprint);
        return Result.success();
    }

    private String getFingerprint(HttpServletRequest request) {
        // 服务端计算设备指纹，不信任客户端提供的值
        // 防止伪造指纹绕过阅读限制
        String ua = request.getHeader("User-Agent");
        String ip = IpUtil.getClientIp(request);
        return AccessControlService.generateFingerprint(ua, ip);
    }
}
