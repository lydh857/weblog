package com.blog.infra.security.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {

    private Long id;

    private Long userId;

    private String username;

    private String operation;

    private String module;

    private String description;

    private String requestMethod;

    private String requestUrl;

    private Integer responseCode;

    private String ipAddress;

    private String userAgent;

    private Long executionTime;

    private LocalDateTime createTime;

    private Boolean adminActor;
}
