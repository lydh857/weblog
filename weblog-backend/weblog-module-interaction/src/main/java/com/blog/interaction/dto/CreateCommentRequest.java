package com.blog.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建评论请求
 */
@Data
public class CreateCommentRequest {

    @NotNull(message = "文章ID不能为空")
    @Positive(message = "文章ID不合法")
    private Long postId;

    /** 父评论ID，0或null为一级评论 */
    @PositiveOrZero(message = "父评论ID不合法")
    private Long parentId;

    /** 被回复的用户ID（子评论回复子评论时使用） */
    @Positive(message = "被回复用户ID不合法")
    private Long replyToUserId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过1000字")
    private String content;
}
