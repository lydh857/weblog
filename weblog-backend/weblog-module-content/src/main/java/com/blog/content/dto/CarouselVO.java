package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播配置视图对象（门户端返回）
 */
@Data
@Schema(description = "轮播配置视图对象")
public class CarouselVO {

  @Schema(description = "ID")
  private Long id;

  @Schema(description = "类型：article 或 image")
  private String type;

  @Schema(description = "标题")
  private String title;

  @Schema(description = "描述")
  private String description;

  @Schema(description = "背景图片URL")
  private String imageUrl;

  @Schema(description = "跳转链接")
  private String linkUrl;

  @Schema(description = "关联文章ID")
  private Long articleId;

  @Schema(description = "文章 slug（article 类型）")
  private String slug;

  @Schema(description = "排序权重")
  private Integer sortOrder;

  @Schema(description = "是否启用")
  private Boolean isEnabled;

  @Schema(description = "生效开始时间")
  private LocalDateTime startTime;

  @Schema(description = "生效结束时间")
  private LocalDateTime endTime;
}
