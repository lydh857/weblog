package com.blog.api.portal;

import com.blog.common.util.IpUtil;
import com.blog.common.result.Result;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.infra.captcha.model.CaptchaGenerateVO;
import com.blog.infra.captcha.model.CaptchaRiskContext;
import com.blog.infra.captcha.model.CaptchaRefreshRequest;
import com.blog.infra.captcha.model.CaptchaVerifyRequest;
import com.blog.infra.captcha.model.CaptchaVerifyVO;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.api.portal.support.DeviceFingerprintService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 滑块验证码接口
 */
@Tag(name = "验证码", description = "滑块拼图验证码生成、验证、刷新")
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String CAPTCHA_GENERATE_RATE_LIMIT_KEY = "captcha_generate_rate_limit";
    private static final String CAPTCHA_VERIFY_RATE_LIMIT_KEY = "captcha_verify_rate_limit";

    @Operation(summary = "生成验证码")
    @GetMapping("/generate")
    @RateLimit(key = "captchaGenerate", capacity = 10, seconds = 60)
    public Result<CaptchaGenerateVO> generate(@RequestParam String scene,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "captchaGenerate",
                CAPTCHA_GENERATE_RATE_LIMIT_KEY,
                10,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "验证码请求过于频繁，请稍后再试"
        );
        return Result.success(captchaService.generateCaptcha(scene, buildRiskContext(request, response)));
    }

    @Operation(summary = "验证滑块")
    @PostMapping("/verify")
    @RateLimit(key = "captchaVerify", capacity = 20, seconds = 60)
    public Result<CaptchaVerifyVO> verify(@Valid @RequestBody CaptchaVerifyRequest req,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "captchaVerify",
                CAPTCHA_VERIFY_RATE_LIMIT_KEY,
                20,
                1,
                180,
                60,
                IpUtil.getClientIp(request),
                "验证码验证过于频繁，请稍后再试"
        );
        return Result.success(captchaService.verifyCaptcha(req, buildRiskContext(request, response)));
    }

    @Operation(summary = "刷新验证码")
    @PostMapping("/refresh")
    @RateLimit(key = "captchaGenerate", capacity = 10, seconds = 60)
    public Result<CaptchaGenerateVO> refresh(@Valid @RequestBody CaptchaRefreshRequest req,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        dynamicRateLimitPolicyService.enforcePerIp(
                "captchaGenerate",
                CAPTCHA_GENERATE_RATE_LIMIT_KEY,
                10,
                1,
                120,
                60,
                IpUtil.getClientIp(request),
                "验证码请求过于频繁，请稍后再试"
        );
        return Result.success(captchaService.refreshCaptcha(req.getOldToken(), req.getScene(), buildRiskContext(request, response)));
    }

    private CaptchaRiskContext buildRiskContext(HttpServletRequest request, HttpServletResponse response) {
        return CaptchaRiskContext.builder()
                .clientIp(IpUtil.getClientIp(request))
                .deviceFingerprint(deviceFingerprintService.resolveFingerprint(request, response))
                .sessionId(request.getSession(true).getId())
                .build();
    }
}
