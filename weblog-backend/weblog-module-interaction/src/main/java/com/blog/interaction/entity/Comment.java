package com.blog.interaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论实体
 */
@Data
@TableName("t_comment")
public class Comment implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long userId;

    /** 父评论ID，0为一级评论 */
    private Long parentId;

    /** 被回复的用户ID（子评论回复子评论时使用） */
    private Long replyToUserId;

    /** 评论内容（纯文本） */
    private String content;

    private Integer likeCount;

    /** pending / approved / rejected / spam */
    private String status;

    /** 拒绝原因，仅 rejected 状态使用 */
    private String rejectReason;

    private Boolean isTop;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
