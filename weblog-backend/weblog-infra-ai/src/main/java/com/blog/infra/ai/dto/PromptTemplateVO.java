package com.blog.infra.ai.dto;

import lombok.Data;

/**
 * 提示词模板 VO
 */
@Data
public class PromptTemplateVO {

  private String templateKey;
  private String name;
  private String description;
  private String systemPrompt;
  private String userPromptTemplate;
  /** 可用变量列表（JSON 数组字符串） */
  private String variables;
  private Boolean isCustomized;
}
