package com.blog.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 黑名单实体（兼容表名 t_ip_blacklist）
 */
@Data
@TableName("t_ip_blacklist")
public class BlacklistEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 封禁类型：IP / USER */
    private String blockType;

    /** 封禁对象值：IP 或 userId 字符串 */
    private String targetValue;

    /** 用户ID（仅 USER 类型） */
    private Long userId;

    /** 对象展示信息（IP/邮箱/昵称） */
    private String subject;

    /** 兼容历史字段：IP 地址 */
    private String ipAddress;

    private String reason;

    /** 到期时间，NULL 表示永久 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
