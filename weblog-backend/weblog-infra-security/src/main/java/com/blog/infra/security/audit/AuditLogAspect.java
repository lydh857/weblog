package com.blog.infra.security.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.util.IpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
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
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        int responseCode = 200;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            responseCode = 500;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            try {
                saveLogAsync(auditLog, responseCode, executionTime);
            } catch (Exception e) {
                log.error("审计日志记录失败", e);
            }
        }
    }

    /**
     * 异步保存审计日志，避免阻塞业务接口响应
     */
    private void saveLogAsync(AuditLog auditLog, int responseCode, long executionTime) {
        // 在当前线程获取 Request 信息（异步线程无法访问 RequestContext）
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        AuditLogEntry entry = new AuditLogEntry();
        try {
            if (StpUtil.isLogin()) {
                entry.setUserId(StpUtil.getLoginIdAsLong());
            }
        } catch (Exception ignored) {
        }
        entry.setOperation(auditLog.operation());
        entry.setModule(auditLog.module());
        entry.setDescription(auditLog.description());
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
}
