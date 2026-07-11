package com.blog.content.dto;

import lombok.Data;

/**
 * 对话消息 VO
 */
@Data
public class ChatMessageVO {

  /** 角色：user / assistant */
  private String role;

  /** 消息内容 */
  private String content;
}
