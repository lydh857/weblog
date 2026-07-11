package com.blog.infra.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提示词模板更新请求 VO
 */
@Data
public class PromptUpdateVO {

  @NotBlank(message = "系统提示词不能为空")
  private String systemPrompt;

  private String userPromptTemplate;
}
