package com.blog.infra.captcha.model;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 验证码校验请求
 */
@Data
public class CaptchaVerifyRequest {
    @NotBlank(message = "captchaToken 不能为空")
    @Size(max = 36, message = "captchaToken 格式无效")
    @Pattern(regexp = "^[a-f0-9\\-]{1,36}$", message = "captchaToken 格式无效")
    private String captchaToken;

    @NotNull(message = "滑动位置不能为空")
    @Min(value = 0, message = "滑动位置无效")
    @Max(value = 500, message = "滑动位置无效")
    private Integer sliderPosition;

    @NotNull(message = "滑动轨迹不能为空")
    @Size(min = 5, max = 500, message = "滑动轨迹数据异常")
    private List<TrackPoint> slideTrack;
}
