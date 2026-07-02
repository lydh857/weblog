package com.blog.infra.security.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 安全事件审计记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventAuditService {

    private final AuditLogMapper auditLogMapper;
    private final JdbcTemplate jdbcTemplate;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    public void recordEvent(String module,
                            String operation,
                            String description,
                            Long userId,
                            String username,
                            String ipAddress,
                            String userAgent,
                            Integer responseCode,
                            String requestMethod,
                            String requestUrl) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setModule(truncate(module, 100));
        entry.setOperation(truncate(operation, 50));
        entry.setDescription(truncate(description, 500));
        entry.setUserId(userId);
        entry.setUsername(resolveUsername(userId, username));
        entry.setResponseCode(responseCode != null ? responseCode : 200);
        entry.setIpAddress(truncate(resolveIpAddress(ipAddress), 64));
        entry.setUserAgent(truncate(resolveUserAgent(userAgent), 500));
        entry.setRequestMethod(truncate(resolveRequestMethod(requestMethod), 20));
        entry.setRequestUrl(truncate(resolveRequestUrl(requestUrl), 500));
        entry.setExecutionTime(0L);
        entry.setCreateTime(LocalDateTime.now());

        CompletableFuture.runAsync(() -> {
            try {
                auditLogMapper.insert(entry);
            } catch (Exception e) {
                log.error("记录安全事件审计日志失败", e);
            }
        }, taskExecutor);
    }

    private String resolveRequestMethod(String requestMethod) {
        if (StringUtils.hasText(requestMethod)) {
            return requestMethod;
        }
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    private String resolveRequestUrl(String requestUrl) {
        if (StringUtils.hasText(requestUrl)) {
            return requestUrl;
        }
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    private String resolveIpAddress(String ipAddress) {
        if (StringUtils.hasText(ipAddress)) {
            return ipAddress;
        }
        HttpServletRequest request = currentRequest();
        return request == null ? null : IpUtil.getClientIp(request);
    }

    private String resolveUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            return userAgent;
        }
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String resolveUsername(Long userId, String username) {
        if (StringUtils.hasText(username)) {
            return username;
        }

        Long currentUserId = userId;
        if (currentUserId == null) {
            try {
                if (StpUtil.isLogin()) {
                    currentUserId = StpUtil.getLoginIdAsLong();
                }
            } catch (Exception ignored) {
            }
        }

        if (currentUserId == null) {
            return null;
        }

        try {
            String resolved = jdbcTemplate.query(
                    "SELECT nickname, email FROM t_user WHERE id = ? AND is_deleted = 0 LIMIT 1",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        String nickname = rs.getString("nickname");
                        if (StringUtils.hasText(nickname)) {
                            return nickname;
                        }
                        String email = rs.getString("email");
                        return StringUtils.hasText(email) ? email : null;
                    },
                    currentUserId
            );
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        } catch (Exception e) {
            log.debug("解析安全事件用户名失败: userId={}, message={}", currentUserId, e.getMessage());
        }

        return "UID:" + currentUserId;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
