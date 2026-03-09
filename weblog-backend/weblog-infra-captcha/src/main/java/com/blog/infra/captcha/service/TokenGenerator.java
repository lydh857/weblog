package com.blog.infra.captcha.service;

import com.blog.infra.captcha.CaptchaProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * HMAC 签名令牌生成器 — 用于生成和校验 Verify_Token。
 * <p>
 * Token 格式：{tokenId}.{hmacSignature}
 * <br>
 * 签名内容：tokenId + "|" + clientIp + "|" + createTime
 */
@Component
public class TokenGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";

    private final CaptchaProperties properties;

    public TokenGenerator(CaptchaProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成带 HMAC 签名的验证令牌。
     *
     * @param clientIp   客户端 IP
     * @param createTime 创建时间戳
     * @return tokenId（用于 Redis key）和完整 token（返回给前端）
     */
    public TokenResult generateToken(String clientIp, long createTime) {
        String tokenId = UUID.randomUUID().toString();
        String signature = computeSignature(tokenId, clientIp, createTime);
        String fullToken = tokenId + TOKEN_SEPARATOR + signature;
        return new TokenResult(tokenId, fullToken);
    }

    /**
     * 从完整 token 中提取 tokenId。
     *
     * @param token 完整 token 字符串
     * @return tokenId，格式无效时返回 null
     */
    public String extractTokenId(String token) {
        if (token == null || !token.contains(TOKEN_SEPARATOR)) {
            return null;
        }
        int idx = token.indexOf(TOKEN_SEPARATOR);
        return token.substring(0, idx);
    }

    /**
     * 校验 token 签名完整性。
     *
     * @param token      完整 token 字符串
     * @param clientIp   客户端 IP
     * @param createTime 创建时间戳
     * @return true 表示签名有效
     */
    public boolean validateSignature(String token, String clientIp, long createTime) {
        if (token == null || !token.contains(TOKEN_SEPARATOR)) {
            return false;
        }
        int idx = token.indexOf(TOKEN_SEPARATOR);
        String tokenId = token.substring(0, idx);
        String signature = token.substring(idx + 1);

        String expectedSignature = computeSignature(tokenId, clientIp, createTime);
        return constantTimeEquals(signature, expectedSignature);
    }

    /**
     * 计算 HMAC-SHA256 签名。
     */
    private String computeSignature(String tokenId, String clientIp, long createTime) {
        String data = tokenId + "|" + clientIp + "|" + createTime;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC 签名计算失败", e);
        }
    }

    /**
     * 常量时间字符串比较，防止时序攻击。
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Token 生成结果
     */
    public record TokenResult(String tokenId, String fullToken) {}
}
