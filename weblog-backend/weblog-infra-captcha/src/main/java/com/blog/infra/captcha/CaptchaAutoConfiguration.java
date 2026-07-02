package com.blog.infra.captcha;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("com.blog.infra.captcha")
@EnableConfigurationProperties(CaptchaProperties.class)
@RequiredArgsConstructor
public class CaptchaAutoConfiguration {

    private final CaptchaProperties properties;

    @PostConstruct
    public void checkSecretKey() {
        if ("default-captcha-secret-change-me".equals(properties.getSecretKey())) {
            log.warn("⚠️ 验证码 HMAC 密钥使用默认值，生产环境请配置 captcha.secret-key！");
        }
    }
}
