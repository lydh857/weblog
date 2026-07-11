package com.blog.content.vo;

import lombok.Data;

/**
 * 标签云 VO（含文章计数）
 */
@Data
public class TagCloudVO {
  private Long id;
  private String name;
  private String slug;
  private String color;
  private Integer postCount;
}
