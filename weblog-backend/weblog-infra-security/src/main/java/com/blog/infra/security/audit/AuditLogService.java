package com.blog.infra.security.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志服务
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 分页查询审计日志
     */
    public IPage<AuditLogEntry> page(int pageNum, int pageSize,
                                     String operation, Long userId,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(operation != null, AuditLogEntry::getOperation, operation)
               .eq(userId != null, AuditLogEntry::getUserId, userId)
               .ge(startTime != null, AuditLogEntry::getCreateTime, startTime)
               .le(endTime != null, AuditLogEntry::getCreateTime, endTime)
               .orderByDesc(AuditLogEntry::getCreateTime);

        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 按 IP 查询登录日志（用于检测异常登录）
     */
    public IPage<AuditLogEntry> queryByIp(String ipAddress, int pageNum, int pageSize) {
        LambdaQueryWrapper<AuditLogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLogEntry::getIpAddress, ipAddress)
               .eq(AuditLogEntry::getOperation, "LOGIN")
               .orderByDesc(AuditLogEntry::getCreateTime);

        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
