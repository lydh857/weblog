package com.blog.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Remember Token 实体
 * 用于实现"记住我"自动登录功能
 */
@Data
@TableName("t_remember_token")
public class RememberToken {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * Token（UUID）
     */
    private String token;

    /**
     * 设备信息（User-Agent 摘要）
     */
    private String deviceInfo;

    /**
     * 最后登录 IP
     */
    private String lastLoginIp;

    /**
     * 是否有效
     */
    private Boolean isValid;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
