package com.blog.common.util;

import cn.hutool.crypto.digest.BCrypt;

import java.security.SecureRandom;

/**
 * 密码工具类
 * 基于 BCrypt 算法加密和校验密码
 */
public final class PasswordUtil {

    private PasswordUtil() {}

    /**
     * 加密密码
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值（$2a$10$...）
     */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 校验密码
     *
     * @param rawPassword    明文密码
     * @param hashedPassword BCrypt 哈希值
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String hashedPassword) {
        return BCrypt.checkpw(rawPassword, hashedPassword);
    }

    /**
     * 生成随机密码（8位，含大小写字母和数字）
     */
    public static String generateRandomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        String all = upper + lower + digits;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        for (int i = 3; i < 8; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }
}
