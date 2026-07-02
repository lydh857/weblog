package com.blog.content.dto;

import lombok.Data;

/**
 * AI 写作请求 VO
 */
@Data
public class WritingReqVO {

  /** 选中的文本 */
  private String text;

  /** 续写上下文 */
  private String context;
}
