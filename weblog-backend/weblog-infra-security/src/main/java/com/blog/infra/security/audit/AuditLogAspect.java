package com.blog.infra.security.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.util.IpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * 审计日志切面
 * 拦截 @AuditLog 注解的方法，异步记录操作日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        int responseCode = 200;
        Long actorUserId = null;

        try {
            if (StpUtil.isLogin()) {
                actorUserId = StpUtil.getLoginIdAsLong();
            }
        } catch (Exception ignored) {
        }

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            if (e instanceof BusinessException businessException) {
                responseCode = businessException.getCode();
            } else {
                responseCode = 500;
            }
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            try {
                saveLogAsync(auditLog, responseCode, executionTime, actorUserId);
            } catch (Exception e) {
                log.error("审计日志记录失败", e);
            }
        }
    }

    /**
     * 异步保存审计日志，避免阻塞业务接口响应
     */
    private void saveLogAsync(AuditLog auditLog, int responseCode, long executionTime, Long actorUserId) {
        // 在当前线程获取 Request 信息（异步线程无法访问 RequestContext）
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        AuditLogEntry entry = new AuditLogEntry();
        if (actorUserId != null) {
            entry.setUserId(actorUserId);
            entry.setUsername(resolveUsername(actorUserId));
        }
        entry.setOperation(auditLog.operation());
        entry.setModule(auditLog.module());
        String appendDescription = asText(request.getAttribute(AuditLog.AUDIT_APPEND_DESCRIPTION_ATTR));
        entry.setDescription(buildDescription(auditLog.description(), appendDescription));
        entry.setRequestMethod(request.getMethod());
        entry.setRequestUrl(request.getRequestURI());
        entry.setResponseCode(responseCode);
        entry.setIpAddress(IpUtil.getClientIp(request));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        entry.setExecutionTime(executionTime);
        entry.setCreateTime(LocalDateTime.now());

        // 异步写入数据库
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                auditLogMapper.insert(entry);
            } catch (Exception e) {
                log.error("异步写入审计日志失败", e);
            }
        }, taskExecutor);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }

        try {
            String username = jdbcTemplate.query(
                    "SELECT nickname, email FROM t_user WHERE id = ? AND is_deleted = 0 LIMIT 1",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        String email = rs.getString("email");
                        if (StringUtils.hasText(email)) {
                            return email;
                        }
                        String nickname = rs.getString("nickname");
                        if (StringUtils.hasText(nickname)) {
                            return nickname;
                        }
                        return null;
                    },
                    userId
            );
            if (StringUtils.hasText(username)) {
                return username;
            }
        } catch (Exception e) {
            log.debug("查询审计日志用户名失败: userId={}, message={}", userId, e.getMessage());
        }

        return "UID:" + userId;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String buildDescription(String baseDescription, String appendDescription) {
        String base = baseDescription == null ? "" : baseDescription.trim();
        String append = appendDescription == null ? "" : appendDescription.trim();
        if (append.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return append;
        }
        return base + " | " + append;
    }
}
