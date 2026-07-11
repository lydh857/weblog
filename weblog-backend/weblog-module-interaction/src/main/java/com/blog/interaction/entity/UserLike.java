package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户点赞记录
 */
@Data
@TableName("t_user_like")
public class UserLike implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** post / comment */
    private String targetType;

    private Long targetId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Boolean isDeleted;
}
