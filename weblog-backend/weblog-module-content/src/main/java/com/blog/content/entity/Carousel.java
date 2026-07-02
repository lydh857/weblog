package com.blog.content.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.blog.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 轮播配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_carousel")
public class Carousel extends BaseEntity {

  /** 类型：article 或 image */
  private String type;

  /** 标题 */
  private String title;

  /** 描述 */
  private String description;

  /** 背景图片URL */
  private String imageUrl;

  /** 跳转链接（image 类型可选） */
  private String linkUrl;

  /** 关联文章ID（article类型） */
  private Long articleId;

  /** 排序权重，降序 */
  private Integer sortOrder;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 生效开始时间 */
  private LocalDateTime startTime;

  /** 生效结束时间 */
  private LocalDateTime endTime;
}
