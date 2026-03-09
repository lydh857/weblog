package com.blog.interaction.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量评论审核请求 VO
 */
@Data
public class BatchReviewReqVO {

  @NotEmpty(message = "评论ID列表不能为空")
  private List<Long> ids;
}
