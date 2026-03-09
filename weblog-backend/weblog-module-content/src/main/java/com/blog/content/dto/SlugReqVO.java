package com.blog.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Slug 生成请求 VO
 */
@Data
public class SlugReqVO {

  @NotBlank(message = "文章标题不能为空")
  private String title;
}
