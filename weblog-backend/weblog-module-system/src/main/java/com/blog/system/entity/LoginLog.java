package com.blog.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志
 */
@Data
@TableName("t_login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 登录类型: user(用户端), admin(管理端)
     */
    private String loginType;

    /**
     * 登录结果: success, failed
     */
    private String result;

    /**
     * 失败原因（如有）
     */
    private String failReason;

    /**
     * 客户端 IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 登录时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
