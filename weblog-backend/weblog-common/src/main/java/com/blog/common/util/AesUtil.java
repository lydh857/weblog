package com.blog.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AES 对称加密工具类
 */
public final class AesUtil {

    private AesUtil() {}

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static SecretKeySpec getSecretKey(String rawKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            // 截取前 16 字节作为 AES-128 密钥
            byte[] truncatedKey = new byte[16];
            System.arraycopy(keyBytes, 0, truncatedKey, 0, 16);
            return new SecretKeySpec(truncatedKey, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key Spec: " + e.getMessage(), e);
        }
    }

    /**
     * 加密
     */
    public static String encrypt(String content, String rawKey) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        try {
            SecretKeySpec keySpec = getSecretKey(rawKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedBytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解密
     */
    public static String decrypt(String base64Content, String rawKey) {
        if (base64Content == null || base64Content.isEmpty()) {
            return base64Content;
        }
        try {
            SecretKeySpec keySpec = getSecretKey(rawKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 启发式判断是否是加密的 Base64 字符串
     */
    public static boolean isEncrypted(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        // Base64 格式且经 AES ECB 块加密后字节数一般为 16 的倍数（且不小于 16）
        try {
            byte[] decoded = Base64.getDecoder().decode(content);
            return decoded.length >= 16 && decoded.length % 16 == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
