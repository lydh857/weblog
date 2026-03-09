package com.blog.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 */
public final class IpUtil {

    private IpUtil() {}

    private static final String[] TRUSTED_PROXIES = {
        "127.0.0.1", "localhost", "0:0:0:0:0:0:0:1"
    };

    /**
     * 获取客户端真实 IP 地址，支持反向代理。
     *
     * 安全说明：
     * - 只信任 X-Forwarded-For 和 X-Real-IP 头中的最后一个 IP（真实客户端）
     * - 排除已知的内网地址作为客户端 IP
     * - 直接访问时使用 request.getRemoteAddr()
     */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // 检查请求是否来自可信的代理服务器
        boolean fromTrustedProxy = isTrustedProxy(request);

        if (!fromTrustedProxy) {
            // 非代理请求，直接使用远程地址
            return remoteAddr;
        }

        // 从代理头获取真实 IP
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取最右边（最原始）的客户端 IP
            ip = ip.trim();
            if (ip.contains(",")) {
                ip = ip.split(",")[ip.split(",").length - 1].trim();
            }
            // 验证是否是有效公网 IP（排除内网）
            if (isValidPublicIp(ip)) {
                return ip;
            }
        }

        // 备用：尝试 X-Real-IP
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            ip = ip.trim();
            if (isValidPublicIp(ip)) {
                return ip;
            }
        }

        // 都无效时使用远程地址
        return remoteAddr;
    }

    /**
     * 检查请求是否来自可信的代理服务器
     */
    private static boolean isTrustedProxy(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null) {
            return false;
        }

        // 本地请求或已知代理 IP
        for (String trusted : TRUSTED_PROXIES) {
            if (trusted.equalsIgnoreCase(remoteAddr)) {
                return true;
            }
        }

        // 检查是否是私有 IP 段（可能是本地代理）
        return isPrivateIp(remoteAddr);
    }

    /**
     * 检查是否为有效的公网 IP
     */
    private static boolean isValidPublicIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // 排除内网 IP
        if (isPrivateIp(ip)) {
            return false;
        }

        // 排除未知/无效 IP
        if ("unknown".equalsIgnoreCase(ip)) {
            return false;
        }

        return true;
    }

    /**
     * 检查是否为私有 IP（内网 IP）
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }

        try {
            // 127.x.x.x - 回环地址
            if (ip.startsWith("127.")) {
                return true;
            }

            // 10.x.x.x
            if (ip.startsWith("10.")) {
                return true;
            }

            // 172.16.x.x - 172.31.x.x
            if (ip.startsWith("172.")) {
                String[] parts = ip.split("\\.");
                if (parts.length >= 2) {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                }
            }

            // 192.168.x.x
            if (ip.startsWith("192.168.")) {
                return true;
            }

            // IPv6 回环
            if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
