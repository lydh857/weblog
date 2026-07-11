package com.blog.content.dto;

import lombok.Data;

/**
 * 标签推荐项
 */
@Data
public class TagSuggestion {

  /** 标签名 */
  private String name;

  /** 是否为已有标签 */
  private Boolean isExisting;

  /** 已有标签ID（已有标签时非空） */
  private Long tagId;
}
