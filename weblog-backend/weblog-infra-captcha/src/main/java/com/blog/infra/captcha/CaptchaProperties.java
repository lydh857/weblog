package com.blog.infra.captcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {
    /**
     * HMAC 签名密钥。
     * 生产环境必须通过 captcha.secret-key 配置自定义密钥！
     */
    private String secretKey = "default-captcha-secret-change-me";

    /**
     * 验证通过凭证有效期（秒）。
     */
    private int verifyTokenExpireSeconds = 120;
}
