package com.blog.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
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

    private static final Set<String> PROD_FORBIDDEN_PATTERNS = Set.of(
            "change-this-in-production",
            "local-dev"
    );

    private final Environment environment;

    @Value("${sa-token.jwt-secret-key:}")
    private String jwtSecretKey;

    @Value("${captcha.secret-key:}")
    private String captchaSecretKey;

    @Value("${blog.security.dev-bypass-enabled:false}")
    private boolean devBypassEnabled;

    public SecurityConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateSecret("JWT_SECRET", jwtSecretKey, 32);
        validateSecret("CAPTCHA_SECRET_KEY", captchaSecretKey, 32);
        validateDevBypassForProd();
        validateTrustedProxiesForProd();
    }

    private void validateDevBypassForProd() {
        if (isProdProfile() && devBypassEnabled) {
            throw new IllegalStateException("生产环境禁止开启 blog.security.dev-bypass-enabled");
        }
    }

    private void validateTrustedProxiesForProd() {
        if (!isProdProfile()) {
            return;
        }

        String trustedProxies = System.getenv("TRUSTED_PROXY_IPS");
        if (!StringUtils.hasText(trustedProxies)) {
            throw new IllegalStateException("生产环境必须配置 TRUSTED_PROXY_IPS，防止伪造代理头导致 IP 识别失真");
        }
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

        if (isProdProfile()) {
            String lower = normalized.toLowerCase();
            boolean containsForbiddenPattern = PROD_FORBIDDEN_PATTERNS.stream()
                    .anyMatch(lower::contains);
            if (containsForbiddenPattern) {
                throw new IllegalStateException(keyName + " 命中生产禁用模式，启动已拒绝");
            }
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
    }
}
