package com.blog.infra.ai.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * AI 客户端封装
 * <p>
 * ChatModel 和 EmbeddingModel 支持运行时热重载：
 * 启动时通过 Spring 自动注入（可能为 null），后续可通过 reconfigure() 动态创建。
 * 使用 volatile 保证多线程可见性。
 */
@Slf4j
@Service
public class AiClientService {

  private volatile ChatModel chatModel;
  private volatile EmbeddingModel embeddingModel;
  private final TokenMeterService tokenMeter;
  private final ApplicationContext applicationContext;
  private final Executor aiExecutor;

  @Autowired
  public AiClientService(
      @Autowired(required = false) ChatModel chatModel,
      @Autowired(required = false) EmbeddingModel embeddingModel,
      TokenMeterService tokenMeter,
      ApplicationContext applicationContext,
      @org.springframework.beans.factory.annotation.Qualifier("aiExecutor") Executor aiExecutor) {
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.tokenMeter = tokenMeter;
    this.applicationContext = applicationContext;
    this.aiExecutor = aiExecutor;
  }

  /**
   * 延迟获取 AiConfigService，避免循环依赖
   */
  private AiConfigService getAiConfigService() {
    return applicationContext.getBean(AiConfigService.class);
  }

  /**
   * 运行时热重载 ChatModel 和 EmbeddingModel
   * <p>
   * 根据新的连接参数创建 OpenAiApi、OpenAiChatModel、OpenAiEmbeddingModel 实例，
   * 替换当前引用。使用 volatile 保证对其他线程立即可见。
   *
   * @param apiKey             API Key
   * @param baseUrl            API Base URL
   * @param model              对话模型名称
   * @param embeddingModelName Embedding 模型名称（可为 null，表示不启用 Embedding）
   */
  public void reconfigure(String apiKey, String baseUrl, String model, String embeddingModelName) {
    log.info("热重载 AI 模型: baseUrl={}, model={}, embeddingModel={}", baseUrl, model, embeddingModelName);

    // 构建 OpenAiApi
    OpenAiApi openAiApi = OpenAiApi.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .build();

    // 构建 ChatModel
    OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
        .model(model)
        .build();
    this.chatModel = OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(chatOptions)
        .build();

    // 构建 EmbeddingModel（如果配置了 Embedding 模型名称）
    if (embeddingModelName != null && !embeddingModelName.isBlank()) {
      OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
          .model(embeddingModelName)
          .build();
      this.embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    log.info("AI 模型热重载完成: chatModel={}, embeddingModel={}",
        this.chatModel != null, this.embeddingModel != null);
  }

  /**
   * 检查 AI 客户端是否可用
   */
  public boolean isAvailable() {
    return chatModel != null;
  }

  /**
   * 检查 Embedding 模型是否可用
   */
  public boolean isEmbeddingAvailable() {
    return embeddingModel != null;
  }

  /**
   * 关闭 AI 模型，释放资源（AI 全局开关关闭时调用）
   */
  public void shutdown() {
    this.chatModel = null;
    this.embeddingModel = null;
    log.info("AI 模型已关闭");
  }

  /**
   * 同步调用（元信息生成等）
   * 设置 temperature=0.8 确保重新生成时产生不同结果
   */
  public String call(String feature, String systemPrompt, String userPrompt) {
    ChatModel cm = requireChatModel();

    Prompt prompt = new Prompt(
      List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
      OpenAiChatOptions.builder().temperature(0.8).build()
    );

    ChatResponse response = cm.call(prompt);
    String result = response.getResult().getOutput().getText();

    // 记录 token 用量
    var usage = response.getMetadata().getUsage();
    tokenMeter.record(feature,
      (int) usage.getPromptTokens(),
      (int) usage.getCompletionTokens());

    return result;
  }

  /**
   * 流式调用（写作助手、问答等），返回 SseEmitter
   */
  public SseEmitter stream(String feature, String systemPrompt, String userPrompt) {
    return stream(feature, List.of(
      new SystemMessage(systemPrompt),
      new UserMessage(userPrompt)
    ), null);
  }

  /**
   * 流式调用（支持多轮对话历史），返回 SseEmitter
   *
   * @param feature    功能标识
   * @param messages   完整消息列表（含 system、历史、当前用户消息）
   * @param onComplete 流式完成后的回调（传入完整回答文本），可为 null
   */
  public SseEmitter stream(String feature, List<Message> messages, Consumer<String> onComplete) {
    ChatModel cm = requireChatModel();

    long timeout = getAiConfigService().getTimeout() * 1000L;
    SseEmitter emitter = new SseEmitter(timeout);

    Prompt prompt = new Prompt(
      messages,
      OpenAiChatOptions.builder().temperature(0.8).build()
    );
    AtomicInteger estimatedTokens = new AtomicInteger(0);
    StringBuilder fullAnswer = new StringBuilder();

    // 用于过滤 <think>...</think> 标签的状态
    final boolean[] insideThink = {false};
    final StringBuilder thinkBuffer = new StringBuilder();

    CompletableFuture.runAsync(() -> {
      try {
        cm.stream(prompt).subscribe(
          chatResponse -> {
            try {
              String content = chatResponse.getResult().getOutput().getText();
              if (content == null || content.isEmpty()) return;

              // 过滤 <think>...</think> 标签（Qwen3 思考模式）
              String filtered = filterThinkTags(content, insideThink, thinkBuffer);
              if (filtered != null && !filtered.isEmpty()) {
                emitter.send(SseEmitter.event().data(filtered));
                fullAnswer.append(filtered);
                estimatedTokens.addAndGet(filtered.length() / 4 + 1);
              }
            } catch (IOException e) {
              log.warn("SSE 发送失败: {}", e.getMessage());
              emitter.completeWithError(e);
            }
          },
          error -> {
            log.error("AI 流式调用失败: {}", error.getMessage());
            try {
              emitter.send(SseEmitter.event().data("[ERROR] AI 调用失败，请稍后重试"));
            } catch (IOException ignored) {
              // 发送错误事件失败，忽略
            }
            emitter.completeWithError(error);
          },
          () -> {
            try {
              // 处理 thinkBuffer 中可能残留的非 think 内容
              if (thinkBuffer.length() > 0 && !insideThink[0]) {
                String remaining = thinkBuffer.toString();
                if (!remaining.isEmpty()) {
                  emitter.send(SseEmitter.event().data(remaining));
                  fullAnswer.append(remaining);
                }
              }
              emitter.send(SseEmitter.event().data("[DONE]"));
              emitter.complete();
              // 估算 token 用量
              int inputEstimate = messages.stream()
                .mapToInt(m -> m.getText().length() / 4 + 1).sum();
              tokenMeter.record(feature, inputEstimate, estimatedTokens.get());
              // 回调完整回答
              if (onComplete != null) {
                onComplete.accept(fullAnswer.toString());
              }
            } catch (IOException e) {
              log.warn("SSE 完成发送失败: {}", e.getMessage());
            }
          }
        );
      } catch (Exception e) {
        log.error("AI 流式调用异常: {}", e.getMessage(), e);
        try {
          emitter.send(SseEmitter.event().data("[ERROR] AI 调用异常，请稍后重试"));
        } catch (IOException ignored) {
          // 发送错误事件失败，忽略
        }
        emitter.completeWithError(e);
      }
    }, aiExecutor);

    emitter.onTimeout(() -> {
      log.warn("SSE 超时");
      emitter.complete();
    });

    return emitter;
  }

  /**
   * Embedding 向量生成
   */
  public float[] embed(String feature, String text) {
    EmbeddingModel em = requireEmbeddingModel();

    EmbeddingResponse response = em.embedForResponse(List.of(text));
    float[] embedding = response.getResult().getOutput();

    // 记录 token 用量（Embedding 只有输入 token）
    var usage = response.getMetadata().getUsage();
    tokenMeter.record(feature,
      (int) usage.getPromptTokens(),
      (int) usage.getCompletionTokens());

    return embedding;
  }

  // ========== 内部方法 ==========

  /**
   * 过滤流式 chunk 中的 &lt;think&gt;...&lt;/think&gt; 标签内容
   * <p>
   * 使用状态机处理跨 chunk 的标签：
   * - insideThink[0] 标记当前是否在 think 标签内
   * - thinkBuffer 缓存可能不完整的标签片段
   */
  private String filterThinkTags(String chunk, boolean[] insideThink, StringBuilder thinkBuffer) {
    StringBuilder result = new StringBuilder();
    thinkBuffer.append(chunk);
    String buf = thinkBuffer.toString();

    int i = 0;
    while (i < buf.length()) {
      if (insideThink[0]) {
        // 在 think 标签内，寻找 </think>
        int closeIdx = buf.indexOf("</think>", i);
        if (closeIdx >= 0) {
          insideThink[0] = false;
          i = closeIdx + "</think>".length();
        } else {
          // 还没找到关闭标签，可能跨 chunk，保留缓冲等下一个 chunk
          thinkBuffer.setLength(0);
          // 保留最后可能是 "</think>" 前缀的部分
          int keepFrom = Math.max(i, buf.length() - "</think>".length());
          thinkBuffer.append(buf.substring(keepFrom));
          return result.toString();
        }
      } else {
        // 不在 think 标签内，寻找 <think>
        int openIdx = buf.indexOf("<think>", i);
        if (openIdx >= 0) {
          // 输出 <think> 之前的内容
          result.append(buf, i, openIdx);
          insideThink[0] = true;
          i = openIdx + "<think>".length();
        } else {
          // 没有 <think>，但可能有不完整的 "<think" 前缀
          int safeEnd = buf.length();
          for (int j = Math.max(i, buf.length() - "<think>".length()); j < buf.length(); j++) {
            if ("<think>".startsWith(buf.substring(j))) {
              safeEnd = j;
              break;
            }
          }
          result.append(buf, i, safeEnd);
          thinkBuffer.setLength(0);
          if (safeEnd < buf.length()) {
            thinkBuffer.append(buf.substring(safeEnd));
          }
          return result.toString();
        }
      }
    }

    thinkBuffer.setLength(0);
    return result.toString();
  }

  /**
   * 获取 ChatModel，不可用时抛异常
   */
  private ChatModel requireChatModel() {
    ChatModel cm = this.chatModel;
    if (cm == null) {
      throw new BusinessException(ResultCode.AI_DISABLED, "AI 功能未启用，请先在管理后台配置 API Key 并开启 AI");
    }
    return cm;
  }

  /**
   * 获取 EmbeddingModel，不可用时抛异常
   */
  private EmbeddingModel requireEmbeddingModel() {
    EmbeddingModel em = this.embeddingModel;
    if (em == null) {
      throw new BusinessException(ResultCode.AI_DISABLED, "AI Embedding 模型未配置，请先在管理后台配置 API Key 并开启 AI");
    }
    return em;
  }
}
