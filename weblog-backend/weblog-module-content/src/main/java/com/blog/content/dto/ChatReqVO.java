package com.blog.content.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 写作自由对话请求 VO
 */
@Data
public class ChatReqVO {

  /** 文章上下文 */
  private String articleContext;

  /** 对话历史 */
  private List<ChatMessageVO> history;

  /** 用户消息 */
  private String userMessage;
}
