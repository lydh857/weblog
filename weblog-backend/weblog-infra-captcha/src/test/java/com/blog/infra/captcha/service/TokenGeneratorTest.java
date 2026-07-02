package com.blog.infra.captcha.service;

import com.blog.infra.captcha.CaptchaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenGeneratorTest {

    @Test
    void shouldBindSignatureToScene() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setSecretKey("test-captcha-secret-key-at-least-32-chars");
        TokenGenerator tokenGenerator = new TokenGenerator(properties);

        long createTime = 1_700_000_000_000L;
        TokenGenerator.TokenResult token = tokenGenerator.generateToken("127.0.0.1", "login-password", createTime);

        assertTrue(tokenGenerator.validateSignature(token.fullToken(), "127.0.0.1", "login-password", createTime));
        assertFalse(tokenGenerator.validateSignature(token.fullToken(), "127.0.0.1", "register", createTime));
    }
}
