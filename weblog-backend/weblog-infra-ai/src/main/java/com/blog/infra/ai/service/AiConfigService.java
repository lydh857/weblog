package com.blog.infra.ai.service;

import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.entity.AiConfig;
import com.blog.infra.ai.mapper.AiConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * AI 配置服务（统一管理配置的读写与持久化）
 * <p>
 * 架构：
 * <ul>
 *   <li>数据库 t_ai_config（单行结构化表）负责持久化</li>
 *   <li>AiProperties 内存对象作为本地缓存，高频读取零开销</li>
 *   <li>更新时双写（内存 + DB）保证一致性</li>
 *   <li>连接参数变更时通过 AiClientService.reconfigure() 热重载模型</li>
 * </ul>
 */
@Slf4j
@Service
public class AiConfigService {

  private final AiConfigMapper configMapper;
  private final AiProperties aiProperties;
  private final ApplicationContext applicationContext;

  public AiConfigService(
      AiConfigMapper configMapper,
      AiProperties aiProperties,
      ApplicationContext applicationContext) {
    this.configMapper = configMapper;
    this.aiProperties = aiProperties;
    this.applicationContext = applicationContext;
  }

  /**
   * 延迟获取 AiClientService，避免循环依赖
   */
  private AiClientService getAiClientService() {
    return applicationContext.getBean(AiClientService.class);
  }

  // ========== 启动加载 ==========

  /**
   * 应用启动后从数据库加载配置，覆盖 AiProperties 内存值
   */
  @EventListener(ApplicationReadyEvent.class)
  public void loadFromDb() {
    try {
      AiConfig config = getConfigRow();
      if (config == null) {
        log.info("数据库中无 AI 配置记录，使用 application.yml 默认值");
        return;
      }
      applyToProperties(config);
      log.info("已从数据库加载 AI 配置: enabled={}, provider={}, model={}",
        aiProperties.isEnabled(), aiProperties.getProvider(), aiProperties.getModel());

      // 如果数据库中 AI 已启用且有有效 API Key，热重载模型
      if (aiProperties.isEnabled() && isValidApiKey(aiProperties.getApiKey())) {
        try {
          getAiClientService().reconfigure(
            aiProperties.getApiKey(),
            aiProperties.getBaseUrl(),
            aiProperties.getModel()
          );
        } catch (Exception e) {
          log.error("启动时热重载 AI 模型失败", e);
        }
      }
    } catch (Exception e) {
      log.error("加载数据库 AI 配置失败，使用 application.yml 默认值", e);
    }
  }

  // ========== 读取接口 ==========

  public int getTimeout() {
    return aiProperties.getTimeout();
  }

  public long getMonthlyTokenLimit() {
    return aiProperties.getMonthlyTokenLimit();
  }

  public boolean isFeatureEnabled(String feature) {
    AiProperties.FeatureToggle f = aiProperties.getFeatures();
    return switch (feature) {
      case "writing" -> f.isWriting();
      case "meta" -> f.isMeta();
      case "commentReview" -> f.isCommentReview();
      case "chat" -> f.isChat();
      default -> {
        log.warn("未知的 AI 功能标识: {}", feature);
        yield false;
      }
    };
  }

  // ========== 写入接口 ==========

  /**
   * 更新配置：同时写入内存和数据库，连接参数变更时自动热重载模型
   * <p>
   * 热重载失败时回滚 enabled 状态，防止"内存已启用但模型为 null"的不一致状态。
   */
  public void updateConfig(AiConfig update) {
    boolean connectionChanged = isConnectionChanged(update);
    boolean previousEnabled = aiProperties.isEnabled();

    // 更新内存
    applyToProperties(update);

    // 持久化到数据库（单行 UPDATE）
    AiConfig row = getConfigRow();
    if (row == null) {
      configMapper.insert(update);
    } else {
      update.setId(row.getId());
      configMapper.updateById(update);
    }

    // 连接参数变更且 AI 已启用 → 热重载模型
    if (connectionChanged && aiProperties.isEnabled() && isValidApiKey(aiProperties.getApiKey())) {
      try {
        getAiClientService().reconfigure(
          aiProperties.getApiKey(),
          aiProperties.getBaseUrl(),
          aiProperties.getModel()
        );
        log.info("AI 配置更新后热重载模型成功");
      } catch (Exception e) {
        log.error("AI 配置更新后热重载模型失败，回滚 enabled 状态", e);
        aiProperties.setEnabled(previousEnabled);
        throw new com.blog.common.exception.BusinessException(
          com.blog.common.result.ResultCode.AI_API_ERROR,
          "模型连接失败，AI 未启用。请检查 API Key 和 Base URL 是否正确: " + e.getMessage()
        );
      }
    }

    // AI 关闭时清空模型引用，释放资源
    if (!aiProperties.isEnabled()) {
      getAiClientService().shutdown();
    }
  }

  /**
   * 测试 AI 连接：使用当前内存中的配置参数发送一个简单请求验证连通性
   *
   * @return 模型返回的测试响应文本
   */
  public String testConnection() {
    if (!isValidApiKey(aiProperties.getApiKey())) {
      throw new com.blog.common.exception.BusinessException(
        com.blog.common.result.ResultCode.AI_API_ERROR, "请先配置有效的 API Key");
    }

    try {
      // 临时创建模型实例进行测试，不影响当前运行中的模型
      var openAiApi = org.springframework.ai.openai.api.OpenAiApi.builder()
        .apiKey(aiProperties.getApiKey())
        .baseUrl(aiProperties.getBaseUrl())
        .build();

      var chatOptions = org.springframework.ai.openai.OpenAiChatOptions.builder()
        .model(aiProperties.getModel())
        .maxTokens(20)
        .build();

      var testModel = org.springframework.ai.openai.OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(chatOptions)
        .build();

      var prompt = new org.springframework.ai.chat.prompt.Prompt(
        java.util.List.of(new org.springframework.ai.chat.messages.UserMessage("请回复'连接成功'")));
      var response = testModel.call(prompt);
      return response.getResult().getOutput().getText();
    } catch (Exception e) {
      log.error("AI 连接测试失败", e);
      throw new com.blog.common.exception.BusinessException(
        com.blog.common.result.ResultCode.AI_API_ERROR,
        "连接测试失败: " + e.getMessage()
      );
    }
  }

  // ========== 内部方法 ==========

  /**
   * 获取数据库中的配置行（始终只有一行）
   */
  private AiConfig getConfigRow() {
    return configMapper.selectOne(null);
  }

  /**
   * 判断连接参数是否变更
   */
  private boolean isConnectionChanged(AiConfig update) {
    if (update.getEnabled() != null && update.getEnabled() != aiProperties.isEnabled()) return true;
    if (update.getApiKey() != null && !update.getApiKey().equals(aiProperties.getApiKey())) return true;
    if (update.getBaseUrl() != null && !update.getBaseUrl().equals(aiProperties.getBaseUrl())) return true;
    if (update.getModel() != null && !update.getModel().equals(aiProperties.getModel())) return true;
    return false;
  }

  /**
   * 判断 API Key 是否有效（非空、非 placeholder）
   */
  private boolean isValidApiKey(String apiKey) {
    return apiKey != null && !apiKey.isBlank() && !"disabled-placeholder".equals(apiKey);
  }

  /**
   * 将数据库配置应用到 AiProperties 内存对象
   */
  private void applyToProperties(AiConfig config) {
    if (config.getEnabled() != null) aiProperties.setEnabled(config.getEnabled());
    if (config.getProvider() != null) aiProperties.setProvider(config.getProvider());
    if (config.getApiKey() != null) aiProperties.setApiKey(config.getApiKey());
    if (config.getBaseUrl() != null) aiProperties.setBaseUrl(config.getBaseUrl());
    if (config.getModel() != null) aiProperties.setModel(config.getModel());
    if (config.getMaxTokens() != null) aiProperties.setMaxTokens(config.getMaxTokens());
    if (config.getTimeout() != null) aiProperties.setTimeout(config.getTimeout());
    if (config.getMonthlyTokenLimit() != null) aiProperties.setMonthlyTokenLimit(config.getMonthlyTokenLimit());

    AiProperties.FeatureToggle features = aiProperties.getFeatures();
    if (config.getFeatureWriting() != null) features.setWriting(config.getFeatureWriting());
    if (config.getFeatureMeta() != null) features.setMeta(config.getFeatureMeta());
    if (config.getFeatureCommentReview() != null) features.setCommentReview(config.getFeatureCommentReview());
    if (config.getFeatureChat() != null) features.setChat(config.getFeatureChat());
  }
}
