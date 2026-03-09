package com.blog.interaction.dto;

import lombok.Data;

/**
 * 创建评论请求
 */
@Data
public class CreateCommentRequest {

    private Long postId;

    /** 父评论ID，0或null为一级评论 */
    private Long parentId;

    /** 被回复的用户ID（子评论回复子评论时使用） */
    private Long replyToUserId;

    private String content;
}
