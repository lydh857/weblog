package com.blog.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量设置置顶请求
 */
@Data
public class BatchSetTopRequest {

  @NotEmpty(message = "文章ID列表不能为空")
  private List<Long> ids;

  @NotNull(message = "置顶状态不能为空")
  private Boolean isTop;
}
