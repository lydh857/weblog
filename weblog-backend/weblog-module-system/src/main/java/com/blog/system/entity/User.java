package com.blog.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    private String email;

    /** BCrypt 加密密码，长度 100 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    private String bio;

    /** user / admin */
    private String role;

    /** enabled / disabled / locked */
    private String status;

    private String githubId;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private Integer failedLoginAttempts;

    private LocalDateTime lockUntil;

    /** 是否永久禁言 */
    private Boolean mutedPermanent;

    /** 禁言截止时间，空表示未限时禁言 */
    private LocalDateTime mutedUntil;

    /** 禁言原因 */
    private String mutedReason;
}
