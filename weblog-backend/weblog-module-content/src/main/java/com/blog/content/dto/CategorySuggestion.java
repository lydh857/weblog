package com.blog.content.dto;

import lombok.Data;

/**
 * 分类推荐项
 */
@Data
public class CategorySuggestion {

  /** 分类名 */
  private String name;

  /** 是否为已有分类 */
  private Boolean isExisting;

  /** 已有分类ID */
  private Long categoryId;

  /** 父分类名 */
  private String parentName;
}
