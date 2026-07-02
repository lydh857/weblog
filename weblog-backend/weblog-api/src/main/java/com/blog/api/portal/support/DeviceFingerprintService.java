package com.blog.api.portal.support;

import com.blog.common.util.IpUtil;
import com.blog.interaction.service.AccessControlService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 服务端设备指纹服务（HttpOnly Cookie + HMAC 签名）
 */
@Service
public class DeviceFingerprintService {

    private static final String DEVICE_COOKIE_NAME = "weblog_device_id";
    private static final Duration DEVICE_COOKIE_TTL = Duration.ofDays(180);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${captcha.secret-key:}")
    private String signingSecret;

    public String resolveFingerprint(HttpServletRequest request, HttpServletResponse response) {
        String deviceId = resolveOrCreateDeviceId(request, response);
        String clientIp = IpUtil.getClientIp(request);

        // 密钥缺失或签名不可用时，退化为稳定指纹（避免每次请求随机化导致限读失效）
        if (deviceId == null) {
            String userAgent = request.getHeader("User-Agent");
            String fallbackDeviceId = "ua:" + (userAgent != null ? userAgent : "unknown");
            return AccessControlService.generateFingerprint(fallbackDeviceId, clientIp);
        }

        return AccessControlService.generateFingerprint(deviceId, clientIp);
    }

    private String resolveOrCreateDeviceId(HttpServletRequest request, HttpServletResponse response) {
        if (!hasSigningSecret()) {
            return null;
        }

        String signedCookie = readCookie(request, DEVICE_COOKIE_NAME);
        String deviceId = parseSignedDeviceId(signedCookie);
        if (deviceId != null) {
            return deviceId;
        }

        String newDeviceId = UUID.randomUUID().toString().replace("-", "");
        writeDeviceCookie(response, request, signDeviceId(newDeviceId));
        return newDeviceId;
    }

    private String parseSignedDeviceId(String signedCookie) {
        if (signedCookie == null || signedCookie.isBlank()) {
            return null;
        }

        int splitIndex = signedCookie.lastIndexOf('.');
        if (splitIndex <= 0 || splitIndex >= signedCookie.length() - 1) {
            return null;
        }

        String deviceId = signedCookie.substring(0, splitIndex);
        String signature = signedCookie.substring(splitIndex + 1);
        if (!deviceId.matches("^[a-fA-F0-9]{32}$")) {
            return null;
        }

        String expectedSignature = computeSignature(deviceId);
        if (expectedSignature == null) {
            return null;
        }

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = signature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            return null;
        }

        return deviceId;
    }

    private String signDeviceId(String deviceId) {
        String signature = computeSignature(deviceId);
        if (signature == null) {
            return null;
        }
        return deviceId + "." + signature;
    }

    private String computeSignature(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || !hasSigningSecret()) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(deviceId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeDeviceCookie(HttpServletResponse response,
                                   HttpServletRequest request,
                                   String signedDeviceId) {
        if (signedDeviceId == null || signedDeviceId.isBlank()) {
            return;
        }

        ResponseCookie cookie = ResponseCookie.from(DEVICE_COOKIE_NAME, signedDeviceId)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(DEVICE_COOKIE_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto);
    }

    private boolean hasSigningSecret() {
        return signingSecret != null && !signingSecret.isBlank();
    }
}
