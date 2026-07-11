package com.blog.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 媒体资源统计视图对象
 */
@Data
public class MediaStatsVO {

  /** 文件总数 */
  private long totalCount;

  /** 文件总大小（字节） */
  private long totalSize;

  /** 各类型统计 */
  private List<UsageTypeStat> usageTypeStats = new ArrayList<>();

  @Data
  public static class UsageTypeStat {
    /** 用途类型 */
    private String usageType;

    /** 文件数量 */
    private long fileCount;

    /** 文件总大小（字节） */
    private long totalSize;
  }
}
