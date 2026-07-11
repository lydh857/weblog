package com.blog.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 翻译请求 VO
 */
@Data
public class TranslateReqVO {

  @NotBlank(message = "翻译文本不能为空")
  private String text;

  /** 目标语言：zh / en */
  @NotBlank(message = "目标语言不能为空")
  private String targetLang;
}
