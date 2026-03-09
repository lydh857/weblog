package com.blog.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 */
public final class IpUtil {

    private IpUtil() {}

    /**
     * 获取客户端真实 IP 地址，支持反向代理。
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
