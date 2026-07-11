package com.blog.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * IP 地址工具类
 */
public final class IpUtil {

    private IpUtil() {}

    private static final Set<String> DEFAULT_TRUSTED_PROXIES = Set.of(
            "127.0.0.1", "localhost", "0:0:0:0:0:0:0:1", "::1"
    );

    /**
     * 可信代理列表（默认仅本机代理，可通过环境变量 TRUSTED_PROXY_IPS 追加）
     */
    private static final Set<String> TRUSTED_PROXIES = loadTrustedProxies();

    /**
     * 获取客户端真实 IP 地址，支持反向代理。
     *
     * 安全说明：
     * - 仅当请求来自可信代理时，才解析 X-Forwarded-For / X-Real-IP
     * - X-Forwarded-For 按从右到左提取首个公网地址，降低伪造头部影响
     * - 排除内网地址作为客户端公网 IP
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
        String ip = extractClientIpFromXff(request.getHeader("X-Forwarded-For"));
        if (ip != null) {
            return ip;
        }

        // 备用：尝试 X-Real-IP
        ip = normalizeAddress(request.getHeader("X-Real-IP"));
        if (isValidPublicIp(ip)) {
            return ip;
        }

        // 都无效时使用远程地址
        return remoteAddr;
    }

    /**
     * 检查请求是否来自可信的代理服务器
     */
    private static boolean isTrustedProxy(HttpServletRequest request) {
        String remoteAddr = normalizeAddress(request.getRemoteAddr());
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return false;
        }
        return TRUSTED_PROXIES.contains(remoteAddr);
    }

    private static Set<String> loadTrustedProxies() {
        Set<String> proxies = new HashSet<>(DEFAULT_TRUSTED_PROXIES);
        String extra = System.getenv("TRUSTED_PROXY_IPS");
        if (extra == null || extra.isBlank()) {
            return proxies;
        }

        for (String raw : extra.split(",")) {
            String normalized = normalizeAddress(raw);
            if (normalized != null && !normalized.isEmpty()) {
                proxies.add(normalized);
            }
        }
        return proxies;
    }

    private static String extractClientIpFromXff(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return null;
        }

        String[] parts = xForwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalizeAddress(parts[i]);
            if (candidate == null || candidate.isEmpty() || "unknown".equals(candidate)) {
                continue;
            }
            if (isValidPublicIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizeAddress(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
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
