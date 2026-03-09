package com.blog.infra.captcha.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 验证码刷新请求
 */
@Data
public class CaptchaRefreshRequest {
    @NotBlank(message = "旧令牌不能为空")
    @Size(max = 36, message = "令牌格式无效")
    @Pattern(regexp = "^[a-f0-9\\-]{1,36}$", message = "令牌格式无效")
    private String oldToken;
}
