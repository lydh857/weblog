package com.blog.content.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 元信息生成结果 VO
 */
@Data
public class AiMetaResultVO {

  /** 文章摘要 */
  private String summary;

  /** SEO 标题 */
  private String seoTitle;

  /** SEO 描述 */
  private String seoDescription;

  /** SEO 关键词 */
  private List<String> seoKeywords;

  /** 推荐标签 */
  private List<TagSuggestion> tags;

  /** 推荐分类 */
  private List<CategorySuggestion> categories;

  /** URL Slug */
  private String slug;
}
