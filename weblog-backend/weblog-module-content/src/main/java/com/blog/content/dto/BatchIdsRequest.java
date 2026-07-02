package com.blog.content.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 通用批量 ID 请求
 */
@Data
public class BatchIdsRequest {

  @NotEmpty(message = "ID列表不能为空")
  private List<Long> ids;
}
