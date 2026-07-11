package com.blog.common.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;

public class CryptoTest {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final int ITERATIONS = 10000;

    public static void main(String[] args) throws Exception {
        String saltBase64 = "4nybtUiStdmbxkAewYts6w==";
        String ivBase64 = "EGsvlGoXH9EptK/us4jIow==";
        String ciphertextBase64 = "pA3p1gftNuHfSoU6LpU0dQ==";
        String secret = "weblogCryptoKey32CharactersLong!";

        System.out.println("Salt: " + saltBase64);
        System.out.println("IV: " + ivBase64);
        System.out.println("Ciphertext: " + ciphertextBase64);
        System.out.println("Secret length: " + secret.length());

        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

        // 派生密钥
        KeySpec spec = new PBEKeySpec(
            secret.toCharArray(),
            salt,
            ITERATIONS,
            KEY_SIZE
        );
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

        System.out.println("Derived key (base64): " + Base64.getEncoder().encodeToString(key.getEncoded()));

        // 解密
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(ciphertext);

        System.out.println("Decrypted: " + new String(decrypted, StandardCharsets.UTF_8));
    }
}
