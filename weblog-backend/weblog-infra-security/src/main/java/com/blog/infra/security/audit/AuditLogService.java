package com.blog.infra.security.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 审计日志服务
 */
@Slf4j
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

    /**
     * 管理端分页查询审计日志
     */
    public IPage<AuditLogEntry> pageForAdmin(int pageNum, int pageSize,
                                             String operation, String module,
                                             String username, String ipAddress) {
        LambdaQueryWrapper<AuditLogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(operation), AuditLogEntry::getOperation, operation)
               .like(StringUtils.hasText(module), AuditLogEntry::getModule, module)
               .like(StringUtils.hasText(username), AuditLogEntry::getUsername, username)
               .like(StringUtils.hasText(ipAddress), AuditLogEntry::getIpAddress, ipAddress)
               .orderByDesc(AuditLogEntry::getCreateTime);

        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 清理指定保留天数之前的审计日志
     */
    public int cleanupOldLogs(int retentionDays) {
        int safeRetentionDays = Math.max(retentionDays, 1);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeRetentionDays);
        int deleted = auditLogMapper.delete(new LambdaQueryWrapper<AuditLogEntry>()
                .lt(AuditLogEntry::getCreateTime, cutoff));
        log.info("清理审计日志完成: retentionDays={}, cutoff={}, deleted={}", safeRetentionDays, cutoff, deleted);
        return deleted;
    }
}
