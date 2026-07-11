package com.blog.content.service;

import com.blog.content.dto.ChatMessageVO;
import com.blog.infra.ai.annotation.AiFeature;
import com.blog.infra.ai.service.AiClientService;
import com.blog.infra.ai.service.PromptTemplateService;
import com.blog.infra.ai.service.PromptTemplateService.RenderedPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 写作助手服务
 */
@Service
@RequiredArgsConstructor
public class AiWritingService {

  private final AiClientService aiClient;
  private final PromptTemplateService promptTemplate;

  private static final String FEATURE = "writing";

  /**
   * 续写
   */
  @AiFeature("writing")
  public SseEmitter continueWriting(String context) {
    RenderedPrompt prompt = promptTemplate.render("writing_continue",
      Map.of("context", context));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 润色
   */
  @AiFeature("writing")
  public SseEmitter polish(String text) {
    RenderedPrompt prompt = promptTemplate.render("writing_polish",
      Map.of("text", text));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 改写
   */
  @AiFeature("writing")
  public SseEmitter rewrite(String text) {
    RenderedPrompt prompt = promptTemplate.render("writing_rewrite",
      Map.of("text", text));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 翻译
   */
  @AiFeature("writing")
  public SseEmitter translate(String text, String targetLang) {
    RenderedPrompt prompt = promptTemplate.render("writing_translate",
      Map.of("text", text, "targetLang", targetLang));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 去重
   */
  @AiFeature("writing")
  public SseEmitter deduplicate(String text) {
    RenderedPrompt prompt = promptTemplate.render("writing_deduplicate",
      Map.of("text", text));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 美化 Markdown 格式
   */
  @AiFeature("writing")
  public SseEmitter format(String text) {
    RenderedPrompt prompt = promptTemplate.render("writing_format",
      Map.of("text", text));
    return aiClient.stream(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
  }

  /**
   * 自由对话（携带文章上下文和对话历史，使用 Spring AI Message 对象）
   */
  @AiFeature("writing")
  public SseEmitter chat(String articleContext, List<ChatMessageVO> history, String userMessage) {
    RenderedPrompt prompt = promptTemplate.render("writing_chat",
      Map.of("articleContext", articleContext, "userMessage", userMessage));

    // 构建完整消息列表：system + 对话历史 + 当前用户消息
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(prompt.systemPrompt()));

    if (history != null && !history.isEmpty()) {
      for (ChatMessageVO msg : history) {
        if ("user".equals(msg.getRole())) {
          messages.add(new UserMessage(msg.getContent()));
        } else if ("assistant".equals(msg.getRole())) {
          messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }

    messages.add(new UserMessage(prompt.userPrompt()));
    return aiClient.stream(FEATURE, messages, null);
  }
}
