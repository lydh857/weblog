package com.blog.infra.ai.dto;

import lombok.Data;

/**
 * AI 配置更新请求 VO
 */
@Data
public class AiConfigUpdateVO {

  private Boolean enabled;
  private String provider;
  private String apiKey;
  private String baseUrl;
  private String model;
  private Integer maxTokens;
  private Integer timeout;
  private Long monthlyTokenLimit;
  private FeatureToggleUpdateVO features;

  @Data
  public static class FeatureToggleUpdateVO {
    private Boolean writing;
    private Boolean meta;
    private Boolean commentReview;
    private Boolean chat;
  }
}
