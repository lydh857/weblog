package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.result.Result;
import com.blog.common.util.IpUtil;
import com.blog.interaction.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端 - 访问控制接口
 */
@Tag(name = "用户端-访问控制", description = "阅读限制、滑块验证解锁")
@RestController
@RequestMapping("/api/portal/access")
@RequiredArgsConstructor
public class AccessControlController {

    private final AccessControlService accessControlService;

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
    public Result<Void> unlock(HttpServletRequest request) {
        String fingerprint = getFingerprint(request);
        accessControlService.unlock(fingerprint);
        return Result.success();
    }

    private String getFingerprint(HttpServletRequest request) {
        // 优先使用客户端传来的指纹，否则服务端计算
        String clientFp = request.getHeader("X-Device-Fingerprint");
        if (clientFp != null && !clientFp.isEmpty()) {
            return clientFp;
        }
        String ua = request.getHeader("User-Agent");
        String ip = IpUtil.getClientIp(request);
        return AccessControlService.generateFingerprint(ua, ip);
    }
}
