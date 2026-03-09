package com.blog.interaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量审核评论状态请求
 */
@Data
public class BatchStatusReqVO {

  @NotEmpty(message = "评论ID列表不能为空")
  private List<Long> ids;

  @NotNull(message = "状态不能为空")
  private String status;
}
