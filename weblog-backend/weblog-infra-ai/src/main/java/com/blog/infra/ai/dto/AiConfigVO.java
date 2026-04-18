package com.blog.infra.ai.dto;

import lombok.Data;

/**
 * AI 配置 VO
 */
@Data
public class AiConfigVO {

  private boolean enabled;
  private String model;
  private int timeout;
  private long monthlyTokenLimit;
  private FeatureToggleVO features;

  @Data
  public static class FeatureToggleVO {
    private boolean writing;
    private boolean meta;
    private boolean commentReview;
    private boolean chat;
  }
}
