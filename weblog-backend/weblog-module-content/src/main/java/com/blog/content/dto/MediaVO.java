package com.blog.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 媒体资源视图对象
 * 在 OssResource 基础上增加 referenced 引用状态字段
 */
@Data
public class MediaVO {

  private Long id;
  private String fileName;
  private String filePath;
  private Long fileSize;
  private String fileType;
  private String mimeType;
  private String url;
  private Long uploaderId;
  private String usageType;
  private LocalDateTime createTime;

  /**
   * 缩略图 URL（OSS 模式下带图片处理参数，本地模式等于原始 URL）
   */
  private String thumbnailUrl;

  /**
   * 引用状态：true=已引用, false=未引用
   */
  private Boolean referenced;

  /**
   * 引用来源列表，记录该资源被哪些文章引用及引用方式
   */
  private List<ReferenceDetail> referenceDetails;

  /**
   * 引用来源详情
   */
  @Data
  public static class ReferenceDetail {
    /** 文章 ID */
    private Long postId;
    /** 文章标题 */
    private String postTitle;
    /** 引用方式：cover=封面图, content=内容图 */
    private String refType;
  }
}
