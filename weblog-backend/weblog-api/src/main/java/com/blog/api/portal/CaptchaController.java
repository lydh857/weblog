package com.blog.api.portal;

import com.blog.common.util.IpUtil;
import com.blog.common.result.Result;
import com.blog.infra.captcha.model.CaptchaGenerateVO;
import com.blog.infra.captcha.model.CaptchaRefreshRequest;
import com.blog.infra.captcha.model.CaptchaVerifyRequest;
import com.blog.infra.captcha.model.CaptchaVerifyVO;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

    @Operation(summary = "生成验证码")
    @GetMapping("/generate")
    @RateLimit(key = "captchaGenerate", capacity = 10, seconds = 60)
    public Result<CaptchaGenerateVO> generate(HttpServletRequest request) {
        return Result.success(captchaService.generateCaptcha(IpUtil.getClientIp(request)));
    }

    @Operation(summary = "验证滑块")
    @PostMapping("/verify")
    @RateLimit(key = "captchaVerify", capacity = 20, seconds = 60)
    public Result<CaptchaVerifyVO> verify(@Valid @RequestBody CaptchaVerifyRequest req,
                                          HttpServletRequest request) {
        return Result.success(captchaService.verifyCaptcha(req, IpUtil.getClientIp(request)));
    }

    @Operation(summary = "刷新验证码")
    @PostMapping("/refresh")
    @RateLimit(key = "captchaGenerate", capacity = 10, seconds = 60)
    public Result<CaptchaGenerateVO> refresh(@Valid @RequestBody CaptchaRefreshRequest req,
                                             HttpServletRequest request) {
        return Result.success(captchaService.refreshCaptcha(req.getOldToken(), IpUtil.getClientIp(request)));
    }
}
