package com.blog.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 元信息生成请求 VO
 */
@Data
public class MetaReqVO {

  @NotBlank(message = "文章标题不能为空")
  private String title;

  @NotBlank(message = "文章内容不能为空")
  private String content;
}
