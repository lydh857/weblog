package com.blog.interaction.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论审核请求 VO
 */
@Data
public class CommentReviewReqVO {

  @NotNull(message = "评论ID不能为空")
  private Long commentId;
}
