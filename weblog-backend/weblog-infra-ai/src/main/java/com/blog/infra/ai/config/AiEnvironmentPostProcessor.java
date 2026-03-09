package com.blog.infra.ai.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 环境后处理器
 * <p>
 * 在 Spring 环境准备阶段执行，根据 blog.ai.enabled 和 blog.ai.api-key 决定：
 * <ul>
 *   <li>AI 禁用时：设置 spring.ai.model.chat=none / spring.ai.model.embedding=none 禁用 Spring AI 自动配置</li>
 *   <li>AI 启用时：将 blog.ai.* 属性映射到 spring.ai.openai.* 属性</li>
 * </ul>
 */
public class AiEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_SOURCE_NAME = "blogAiPropertyMapping";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> properties = new HashMap<>();

    boolean enabled = environment.getProperty("blog.ai.enabled", Boolean.class, false);
    String apiKey = environment.getProperty("blog.ai.api-key", "");

    if (!enabled || apiKey == null || apiKey.isBlank()) {
      // AI 未启用或 API Key 为空 → 禁用 Spring AI 自动配置
      // 设置 placeholder key 防止 Audio/Speech 等自动配置因缺少 key 而崩溃
      properties.put("spring.ai.openai.api-key", "disabled-placeholder");
      properties.put("spring.ai.model.chat", "none");
      properties.put("spring.ai.model.embedding", "none");
      properties.put("spring.ai.model.image", "none");
    } else {
      // AI 已启用 → 将 blog.ai.* 映射到 spring.ai.openai.*
      String baseUrl = environment.getProperty("blog.ai.base-url", "https://api.openai.com/v1");
      String model = environment.getProperty("blog.ai.model", "gpt-4o-mini");
      String embeddingModel = environment.getProperty("blog.ai.embedding-model", "text-embedding-3-small");

      properties.put("spring.ai.openai.api-key", apiKey);
      properties.put("spring.ai.openai.base-url", baseUrl);
      properties.put("spring.ai.openai.chat.options.model", model);
      properties.put("spring.ai.openai.embedding.options.model", embeddingModel);
    }

    // 添加到环境中（优先级低于 application.yml，但高于默认值）
    environment.getPropertySources()
      .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
  }
}
