package com.blog.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 安全配置启动校验，防止弱密钥或空密钥进入运行时。
 */
@Component
public class SecurityConfigValidator {

    private static final Set<String> WEAK_SECRETS = Set.of(
            "dev-jwt-secret-key-change-in-production",
            "dev-captcha-secret-change-in-production",
            "change-me",
            "123456",
            "password"
    );

    private final Environment environment;

    @Value("${sa-token.jwt-secret-key:}")
    private String jwtSecretKey;

    @Value("${captcha.secret-key:}")
    private String captchaSecretKey;

    public SecurityConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateSecret("JWT_SECRET", jwtSecretKey, 32);
        validateSecret("CAPTCHA_SECRET_KEY", captchaSecretKey, 32);
    }

    private void validateSecret(String keyName, String value, int minLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少必要安全配置: " + keyName);
        }

        String normalized = value.trim();
        if (normalized.length() < minLength) {
            throw new IllegalStateException(keyName + " 长度不足，至少 " + minLength + " 字符");
        }

        if (WEAK_SECRETS.contains(normalized.toLowerCase())) {
            throw new IllegalStateException(keyName + " 使用弱密钥，启动已拒绝");
        }
    }
}
