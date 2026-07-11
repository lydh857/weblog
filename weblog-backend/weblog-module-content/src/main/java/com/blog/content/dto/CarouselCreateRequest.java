package com.blog.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建轮播配置请求
 */
@Data
@Schema(description = "创建轮播配置请求")
public class CarouselCreateRequest {

  @Schema(description = "类型：article 或 image")
  @NotBlank(message = "类型不能为空")
  private String type;

  @Schema(description = "标题（article 类型可选，默认使用文章标题）")
  private String title;

  @Schema(description = "描述")
  private String description;

  @Schema(description = "背景图片URL（article 类型可选，默认使用文章封面）")
  private String imageUrl;

  @Schema(description = "跳转链接（image 类型可选）")
  private String linkUrl;

  @Schema(description = "关联文章ID（article类型）")
  private Long articleId;

  @Schema(description = "排序权重")
  private Integer sortOrder;

  @Schema(description = "是否启用")
  private Boolean isEnabled;

  @Schema(description = "生效开始时间")
  private LocalDateTime startTime;

  @Schema(description = "生效结束时间")
  private LocalDateTime endTime;
}
