package com.blog.common.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * 通用校验工具
 */
public final class ValidateUtil {

    private ValidateUtil() {}

    /** 邮箱正则（RFC 5322 简化版） */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    /** URL协议白名单 */
    private static final Pattern SAFE_URL_PATTERN = Pattern.compile(
            "^https?://.*$", Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证邮箱格式
     */
    public static boolean isValidEmail(String email) {
        return StrUtil.isNotBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证密码强度：至少8位，包含大小写字母+数字+特殊字符中的至少3种
     */
    public static boolean isStrongPassword(String password) {
        if (StrUtil.isBlank(password) || password.length() < 8) {
            return false;
        }
        int types = 0;
        if (password.matches(".*[a-z].*")) types++;
        if (password.matches(".*[A-Z].*")) types++;
        if (password.matches(".*\\d.*")) types++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) types++;
        return types >= 3;
    }

    /**
     * 验证URL安全（只允许http/https协议）
     */
    public static boolean isSafeUrl(String url) {
        return StrUtil.isNotBlank(url) && SAFE_URL_PATTERN.matcher(url).matches();
    }

    /**
     * 验证是否包含危险协议（javascript:等）
     */
    public static boolean isDangerousUrl(String url) {
        if (StrUtil.isBlank(url)) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("javascript:") || lower.startsWith("data:")
                || lower.startsWith("vbscript:");
    }
}
