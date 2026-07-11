package com.blog.content.dto;

import lombok.Data;

import java.util.List;

/**
 * SEO 信息结果 VO
 */
@Data
public class SeoResultVO {

  private String seoTitle;
  private String seoDescription;
  private List<String> seoKeywords;
}
