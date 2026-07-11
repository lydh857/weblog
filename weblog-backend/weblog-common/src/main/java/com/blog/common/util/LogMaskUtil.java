package com.blog.common.util;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具类
 * 对密码、Token、身份证等敏感信息进行掩码处理
 */
public final class LogMaskUtil {

    private LogMaskUtil() {}

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(password|passwd|pwd)[\"':\\s=]+[\"']?([^\"',\\s}{\\]]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("(token|authorization|jwt|secret|api[_-]?key)[\"':\\s=]+[\"']?([^\"',\\s}{\\]]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(1[3-9]\\d)(\\d{4})(\\d{4})");

    /**
     * 对日志消息进行脱敏
     */
    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = PASSWORD_PATTERN.matcher(message).replaceAll("$1=***");
        result = TOKEN_PATTERN.matcher(result).replaceAll("$1=***");
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***@$2");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$3");
        return result;
    }
}
