package com.blog.infra.ai.dto;

import lombok.Data;

/**
 * AI 配置 VO（API Key 脱敏显示）
 */
@Data
public class AiConfigVO {

  private boolean enabled;
  private String provider;
  /** 脱敏后的 API Key（如 sk-****abcd） */
  private String apiKey;
  private String baseUrl;
  private String model;
  private int maxTokens;
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
