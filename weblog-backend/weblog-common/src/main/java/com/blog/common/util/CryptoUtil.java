package com.blog.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class CryptoUtil {

    private static final String RSA_ALGORITHM = "RSA";
    // 使用 OAEPWithSHA-1AndMGF1Padding (与 jsencrypt 默认 OAEP 模式兼容)
    // 密码已通过 AES-GCM 加密，安全性有保障
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${blog.crypto.rsa-private-key:}")
    private String rsaPrivateKey;

    /**
     * 解密密码
     * 格式: {encryptedKey, encryptedData, iv, tag} (Base64编码)
     *
     * @param encryptedData 加密数据，JSON格式: {"key":"base64","data":"base64","iv":"base64","tag":"base64"}
     * @return 解密后的明文密码
     * @throws IllegalArgumentException 如果数据不是加密格式
     */
    public String decryptPassword(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return null;
        }

        log.info("=== decryptPassword START ===");
        log.info("Received password data, length: {}, first 100 chars: {}", encryptedData.length(), encryptedData.substring(0, Math.min(100, encryptedData.length())));
        log.info("Full data: {}", encryptedData);

        // 检查是否是 JSON 格式（以 { 开头）
        String trimmed = encryptedData.trim();
        if (!trimmed.startsWith("{")) {
            // 不接受明文密码，必须加密传输
            log.error("Received plain text password, encryption required!");
            throw new IllegalArgumentException("Password must be encrypted");
        }

        try {
            // 解析加密数据 JSON
            String encryptedKey = extractJsonValue(encryptedData, "key");
            String encryptedPassword = extractJsonValue(encryptedData, "data");
            String ivBase64 = extractJsonValue(encryptedData, "iv");
            String tagBase64 = extractJsonValue(encryptedData, "tag");

            if (encryptedKey == null || encryptedPassword == null || ivBase64 == null || tagBase64 == null) {
                log.error("Invalid encrypted data format: {}", encryptedData.substring(0, Math.min(50, encryptedData.length())));
                throw new IllegalArgumentException("Invalid encrypted data format");
            }

            log.info("Parsed - key length: {}, data length: {}, iv length: {}, tag length: {}",
                encryptedKey.length(), encryptedPassword.length(), ivBase64.length(), tagBase64.length());

            // 1. 用 RSA 私钥解密 AES 密钥
            SecretKey aesKey = decryptAesKey(encryptedKey);

            // 2. 用 AES-GCM 解密密码
            String decryptedPassword = decryptPasswordWithAes(encryptedPassword, aesKey, ivBase64, tagBase64);

            // 调试：输出解密后的密码长度和前3个字符
            log.info("Decrypted password length: {}, first 3 chars: {}", 
                decryptedPassword.length(), 
                decryptedPassword.length() >= 3 ? decryptedPassword.substring(0, 3) : decryptedPassword);

            return decryptedPassword;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Password decryption failed: {}", e.getMessage());
            throw new IllegalArgumentException("Password decryption failed");
        }
    }

    /**
     * 用 RSA 私钥解密 AES 密钥
     */
    private SecretKey decryptAesKey(String encryptedKeyBase64) throws Exception {
        PrivateKey privateKey = getPrivateKey();
        if (privateKey == null) {
            throw new IllegalStateException("RSA private key not configured");
        }

        Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);

        byte[] encryptedKey = Base64.getDecoder().decode(encryptedKeyBase64);
        byte[] decryptedKeyBytes = rsaCipher.doFinal(encryptedKey);

        return new SecretKeySpec(decryptedKeyBytes, AES_ALGORITHM);
    }

    /**
     * 用 AES-GCM 解密密码
     */
    private String decryptPasswordWithAes(String encryptedPasswordBase64, SecretKey aesKey,
                                          String ivBase64, String tagBase64) throws Exception {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] tag = Base64.getDecoder().decode(tagBase64);
        byte[] encryptedData = Base64.getDecoder().decode(encryptedPasswordBase64);

        // 拼接 IV + 加密数据 + Tag
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedData.length + tag.length);
        buffer.put(iv);
        buffer.put(encryptedData);
        buffer.put(tag);
        byte[] cipherTextWithTag = buffer.array();

        Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);

        byte[] decrypted = aesCipher.doFinal(cipherTextWithTag);
        return new String(decrypted, "UTF-8");
    }

    /**
     * 从配置获取 RSA 私钥
     */
    private PrivateKey getPrivateKey() throws Exception {
        // 直接从环境变量获取（不依赖 Spring 配置）
        String envPrivateKey = System.getProperty("RSA_PRIVATE_KEY");
        if (envPrivateKey != null && !envPrivateKey.isEmpty()) {
            rsaPrivateKey = envPrivateKey;
            log.info("RSA private key loaded from System.getProperty, length: {}", rsaPrivateKey.length());
        }

        if (rsaPrivateKey == null || rsaPrivateKey.isEmpty()) {
            log.error("RSA private key not configured! Please set RSA_PRIVATE_KEY in .env file or blog.crypto.rsa-private-key in application.yml");
            return null;
        }

        log.info("RSA private key loaded, length: {}, first 20 chars: {}", rsaPrivateKey.length(), rsaPrivateKey.substring(0, Math.min(20, rsaPrivateKey.length())));

        // 移除 PEM 头尾和空格（支持 PKCS1 和 PKCS8 格式）
        String privateKeyStr = rsaPrivateKey
                .replace("-----BEGIN PRIVATE KEY-----", "")   // PKCS8
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "") // PKCS1
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        log.info("PrivateKeyStr after trim, length: {}, first 20 chars: {}", privateKeyStr.length(), privateKeyStr.substring(0, Math.min(20, privateKeyStr.length())));

        byte[] keyBytes;
        try {
          keyBytes = Base64.getDecoder().decode(privateKeyStr);
          log.info("Base64 decode SUCCESS, keyBytes length: {}", keyBytes.length);
        } catch (IllegalArgumentException e) {
          log.error("Base64 decode FAILED for private key: {}", e.getMessage());
          throw e;
        }
        log.info("Key bytes length: {}", keyBytes.length);

        // 尝试 PKCS8 格式
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            log.warn("PKCS8 format failed: {}", e.getMessage());
        }

        // 尝试 PKCS1 格式
        try {
            // PKCS1 格式解析需要 ASN.1 解析，这里使用 BouncyCastle 或简化处理
            // 由于 PKCS1 解析复杂，直接抛出错误提示用户
            throw new IllegalArgumentException("PKCS1 format requires conversion to PKCS8");
        } catch (Exception e) {
            log.error("Failed to parse RSA private key: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid RSA private key format");
        }
    }

    /**
     * 简单 JSON 值提取 (不依赖外部库)
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) {
            return null;
        }

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        char quote = json.charAt(valueStart);
        if (quote != '"') {
            return null;
        }

        int valueEnd = valueStart + 1;
        while (valueEnd < json.length()) {
            if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') {
                break;
            }
            valueEnd++;
        }

        return json.substring(valueStart + 1, valueEnd);
    }

    /**
     * 生成随机 AES 密钥 (仅用于测试)
     */
    public static String generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE, new SecureRandom());
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
