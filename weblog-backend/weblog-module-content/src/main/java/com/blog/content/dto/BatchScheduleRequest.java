package com.blog.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量定时发布请求
 */
@Data
public class BatchScheduleRequest {

  @NotEmpty(message = "文章ID列表不能为空")
  private List<Long> ids;

  @NotNull(message = "定时发布时间不能为空")
  private String scheduledTime;

  /** 间隔分钟数（可选） */
  private Integer intervalMinutes;
}
