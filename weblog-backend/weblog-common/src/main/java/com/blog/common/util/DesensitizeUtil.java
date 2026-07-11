package com.blog.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 敏感信息脱敏工具
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {}

    /**
     * 邮箱脱敏：a***@example.com
     */
    public static String email(String email) {
        if (StrUtil.isBlank(email) || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "*" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 手机号脱敏：138****8888
     */
    public static String phone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * IP地址脱敏：192.168.*.*
     */
    public static String ip(String ip) {
        if (StrUtil.isBlank(ip)) return ip;
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return ip;
    }
}
