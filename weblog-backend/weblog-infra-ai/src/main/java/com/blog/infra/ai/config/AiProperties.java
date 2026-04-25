package com.blog.infra.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 配置属性
 * <p>
 * 所有参数均支持运行时热更新：
 * 启动时从 t_ai_config 表加载覆盖 application.yml 默认值，
 * 管理端修改后通过 AiConfigService 双写（内存 + DB），
 * 连接参数变更时自动热重载 ChatModel。
 */
@Data
@ConfigurationProperties(prefix = "blog.ai")
public class AiProperties {

  /** 全局开关 */
  private boolean enabled = false;

  /** 模型提供商（deepseek / openai / qwen） */
  private String provider = "deepseek";

  /** API Key */
  private String apiKey;

  /**
   * API Base URL
   * <p>Spring AI 会自动拼接 /v1/chat/completions，因此 baseUrl 不应包含 /v1 后缀。
   * setter 自动去除末尾多余的 /v1，防止路径重复。
   */
  private String baseUrl = "https://api.deepseek.com";

  public void setBaseUrl(String baseUrl) {
    if (baseUrl != null) {
      baseUrl = baseUrl.replaceAll("/v1/?$", "");
    }
    this.baseUrl = baseUrl;
  }

  /** 对话模型名称 */
  private String model = "deepseek-chat";

  /** 单次最大 token 数 */
  private int maxTokens = 4096;

  /** 超时时间（秒），SseEmitter 超时 = timeout * 1000ms，由管理端 t_ai_config 表热更新 */
  private int timeout = 30;

  /** 月度 token 用量上限（0 表示不限制） */
  private long monthlyTokenLimit = 0;

  /** 各功能开关 */
  private FeatureToggle features = new FeatureToggle();

  @Data
  public static class FeatureToggle {
    private boolean writing = true;
    private boolean meta = true;
    private boolean chat = true;
  }
}
