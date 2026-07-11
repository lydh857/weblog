package com.blog.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量设置禁用状态请求
 */
@Data
public class BatchSetDisabledRequest {

  @NotEmpty(message = "文章ID列表不能为空")
  private List<Long> ids;

  @NotNull(message = "禁用状态不能为空")
  private Boolean isDisabled;
}
