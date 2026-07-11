package com.blog.infra.ai.dto;

import lombok.Data;

/**
 * AI 配置更新结果 VO
 */
@Data
public class AiConfigUpdateResultVO {

  /** 是否需要重启应用（连接参数变更时为 true） */
  private boolean needRestart;

  /** 提示信息 */
  private String message;
}
