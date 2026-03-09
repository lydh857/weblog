package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文章日阅读统计
 */
@Data
@TableName("t_post_read_daily")
public class PostReadDaily implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private LocalDate readDate;

    private Integer readCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
