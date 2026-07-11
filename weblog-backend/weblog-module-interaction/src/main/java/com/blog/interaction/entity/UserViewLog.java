package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户阅读日志（设备维度每日汇总）
 */
@Data
@TableName("t_user_view_log")
public class UserViewLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备指纹MD5 */
    private String deviceHash;

    /** 登录用户ID（未登录为NULL） */
    private Long userId;

    /** 文章ID */
    private Long postId;

    /** 阅读日期 */
    private LocalDate viewDate;

    /** 当日阅读次数 */
    private Integer viewCount;

    /** IP地址 */
    private String ipAddress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
