package com.blog.infra.security.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Data
@TableName("t_audit_log")
public class AuditLogEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 操作类型：LOGIN / LOGOUT / CREATE / UPDATE / DELETE */
    private String operation;

    /** 操作模块 */
    private String module;

    /** 操作描述 */
    private String description;

    private String requestMethod;

    private String requestUrl;

    /** 请求参数（脱敏后） */
    private String requestParams;

    private Integer responseCode;

    private String ipAddress;

    private String userAgent;

    /** 执行时间（毫秒） */
    private Long executionTime;

    private LocalDateTime createTime;
}
