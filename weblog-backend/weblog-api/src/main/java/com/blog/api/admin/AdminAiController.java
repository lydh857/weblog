package com.blog.api.admin;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.result.Result;
import com.blog.content.dto.*;
import com.blog.content.service.AiMetaService;
import com.blog.content.service.AiWritingService;
import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.dto.*;
import com.blog.infra.ai.entity.AiConfig;
import com.blog.infra.ai.entity.AiPromptTemplate;
import com.blog.infra.ai.mapper.AiPromptTemplateMapper;
import com.blog.infra.ai.service.AiConfigService;
import com.blog.infra.ai.service.PromptTemplateService;
import com.blog.infra.ai.service.TokenMeterService;
import com.blog.infra.security.audit.AuditLog;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端 - AI 功能接口
 */
@Tag(name = "管理端-AI功能", description = "AI写作、元信息生成、配置管理、提示词模板")
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

  private final AiWritingService aiWritingService;
  private final AiMetaService aiMetaService;
  private final TokenMeterService tokenMeterService;
  private final PromptTemplateService promptTemplateService;
  private final AiPromptTemplateMapper promptTemplateMapper;
  private final AiProperties aiProperties;
  private final AiConfigService aiConfigService;
  private final DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  private static final String AI_WRITING_RATE_LIMIT_KEY = "ai_writing_rate_limit";
  private static final String AI_CHAT_RATE_LIMIT_KEY = "ai_chat_rate_limit";
  private static final String AI_META_RATE_LIMIT_KEY = "ai_meta_rate_limit";

  // ========== 写作助手接口 ==========

  @Operation(summary = "AI 续写")
  @PostMapping("/writing/continue")
  @RateLimit(key = "ai-writing", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public SseEmitter writingContinue(@RequestBody WritingReqVO req) {
    enforceAiWritingRateLimit();
    return aiWritingService.continueWriting(req.getContext());
  }

  @Operation(summary = "AI 润色")
  @PostMapping("/writing/polish")
  @RateLimit(key = "ai-writing", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public SseEmitter writingPolish(@RequestBody WritingReqVO req) {
    enforceAiWritingRateLimit();
    return aiWritingService.polish(req.getText());
  }

  @Operation(summary = "AI 改写")
  @PostMapping("/writing/rewrite")
  @RateLimit(key = "ai-writing", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public SseEmitter writingRewrite(@RequestBody WritingReqVO req) {
    enforceAiWritingRateLimit();
    return aiWritingService.rewrite(req.getText());
  }

  @Operation(summary = "AI 翻译")
  @PostMapping("/writing/translate")
  @RateLimit(key = "ai-writing", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public SseEmitter writingTranslate(@Valid @RequestBody TranslateReqVO req) {
    enforceAiWritingRateLimit();
    return aiWritingService.translate(req.getText(), req.getTargetLang());
  }

  @Operation(summary = "AI 自由对话")
  @PostMapping("/writing/chat")
  @RateLimit(key = "ai-chat", capacity = 300, seconds = 60, message = "AI 对话请求过于频繁，请稍后重试")
  public SseEmitter writingChat(@RequestBody ChatReqVO req) {
    enforceAiChatRateLimit();
    return aiWritingService.chat(req.getArticleContext(), req.getHistory(), req.getUserMessage());
  }

  // ========== 元信息生成接口 ==========

  @Operation(summary = "一键生成全部元信息")
  @PostMapping("/meta/generate-all")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 生成请求过于频繁，请稍后重试")
  public Result<AiMetaResultVO> generateAll(@Valid @RequestBody MetaReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.generateAll(req.getTitle(), req.getContent()));
  }

  @Operation(summary = "重新生成摘要")
  @PostMapping("/meta/summary")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public Result<String> regenerateSummary(@Valid @RequestBody MetaReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.regenerateSummary(req.getTitle(), req.getContent()));
  }

  @Operation(summary = "重新生成 SEO")
  @PostMapping("/meta/seo")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public Result<SeoResultVO> regenerateSeo(@Valid @RequestBody MetaReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.regenerateSeo(req.getTitle(), req.getContent()));
  }

  @Operation(summary = "重新生成标签推荐")
  @PostMapping("/meta/tags")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public Result<List<TagSuggestion>> regenerateTags(@Valid @RequestBody MetaReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.regenerateTags(req.getTitle(), req.getContent()));
  }

  @Operation(summary = "重新生成分类推荐")
  @PostMapping("/meta/categories")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public Result<List<CategorySuggestion>> regenerateCategories(@Valid @RequestBody MetaReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.regenerateCategories(req.getTitle(), req.getContent()));
  }

  @Operation(summary = "重新生成 Slug")
  @PostMapping("/meta/slug")
  @RateLimit(key = "ai-meta", capacity = 300, seconds = 60, message = "AI 请求过于频繁，请稍后重试")
  public Result<String> regenerateSlug(@Valid @RequestBody SlugReqVO req) {
    enforceAiMetaRateLimit();
    return Result.success(aiMetaService.regenerateSlug(req.getTitle()));
  }

  private void enforceAiWritingRateLimit() {
    dynamicRateLimitPolicyService.enforcePerIp(
            "ai-writing",
            AI_WRITING_RATE_LIMIT_KEY,
            20,
            1,
            300,
            60,
            null,
            "AI 请求过于频繁，请稍后重试"
    );
  }

  private void enforceAiChatRateLimit() {
    dynamicRateLimitPolicyService.enforcePerIp(
            "ai-chat",
            AI_CHAT_RATE_LIMIT_KEY,
            30,
            1,
            300,
            60,
            null,
            "AI 对话请求过于频繁，请稍后重试"
    );
  }

  private void enforceAiMetaRateLimit() {
    dynamicRateLimitPolicyService.enforcePerIp(
            "ai-meta",
            AI_META_RATE_LIMIT_KEY,
            20,
            1,
            300,
            60,
            null,
            "AI 生成请求过于频繁，请稍后重试"
    );
  }

  // ========== 配置管理接口 ==========

  @Operation(summary = "获取 AI 配置")
  @GetMapping("/config")
  public Result<AiConfigVO> getConfig() {

    AiConfigVO vo = new AiConfigVO();
    // 基础参数（来自 AiProperties）
    vo.setEnabled(aiProperties.isEnabled());
    vo.setModel(aiProperties.getModel());
    // 运行时参数（来自 AiProperties 内存缓存，DB 加载）
    vo.setTimeout(aiProperties.getTimeout());
    vo.setMonthlyTokenLimit(aiProperties.getMonthlyTokenLimit());

    AiConfigVO.FeatureToggleVO features = new AiConfigVO.FeatureToggleVO();
    AiProperties.FeatureToggle ft = aiProperties.getFeatures();
    features.setWriting(ft.isWriting());
    features.setMeta(ft.isMeta());
    features.setChat(ft.isChat());
    vo.setFeatures(features);

    return Result.success(vo);
  }

  @Operation(summary = "更新 AI 配置")
  @PutMapping("/config")
  @AuditLog(module = "AI配置", operation = "UPDATE", description = "更新AI配置")
  public Result<AiConfigUpdateResultVO> updateConfig(@RequestBody AiConfigUpdateVO req) {

    // 构建配置实体
    AiConfig config = new AiConfig();
    config.setEnabled(req.getEnabled());
    config.setModel(req.getModel());
    config.setTimeout(req.getTimeout());
    config.setMonthlyTokenLimit(req.getMonthlyTokenLimit());
    if (req.getFeatures() != null) {
      AiConfigUpdateVO.FeatureToggleUpdateVO f = req.getFeatures();
      config.setFeatureWriting(f.getWriting());
      config.setFeatureMeta(f.getMeta());
      config.setFeatureChat(f.getChat());
    }

    // 更新配置（内存 + DB + 热重载模型）
    aiConfigService.updateConfig(config);

    AiConfigUpdateResultVO result = new AiConfigUpdateResultVO();
    result.setNeedRestart(false);
    result.setMessage("配置已保存并立即生效。");
    return Result.success(result);
  }

  @Operation(summary = "测试 AI 连接")
  @PostMapping("/config/test-connection")
  public Result<String> testConnection() {
    String response = aiConfigService.testConnection();
    return Result.success(response);
  }

  @Operation(summary = "获取 Token 用量统计")
  @GetMapping("/token-usage")
  public Result<TokenUsageVO> getTokenUsage(@RequestParam(required = false) String month) {
    return Result.success(tokenMeterService.getMonthlyUsage(month));
  }

  // ========== 提示词模板接口 ==========

  @Operation(summary = "获取所有提示词模板")
  @GetMapping("/prompts")
  public Result<List<PromptTemplateVO>> getPrompts() {
    List<AiPromptTemplate> templates = promptTemplateMapper.selectList(null);
    List<PromptTemplateVO> voList = templates.stream().map(this::toPromptVO).collect(Collectors.toList());
    return Result.success(voList);
  }

  @Operation(summary = "获取单个提示词模板")
  @GetMapping("/prompts/{key}")
  public Result<PromptTemplateVO> getPrompt(@PathVariable String key) {
    AiPromptTemplate template = promptTemplateService.getTemplate(key);
    return Result.success(toPromptVO(template));
  }

  @Operation(summary = "更新提示词模板")
  @PutMapping("/prompts/{key}")
  @AuditLog(module = "AI提示词", operation = "UPDATE", description = "更新提示词模板")
  public Result<Void> updatePrompt(@PathVariable String key, @Valid @RequestBody PromptUpdateVO req) {
    promptTemplateService.updateTemplate(key, req.getSystemPrompt(), req.getUserPromptTemplate());
    return Result.success();
  }

  @Operation(summary = "恢复默认提示词模板")
  @PostMapping("/prompts/{key}/reset")
  @AuditLog(module = "AI提示词", operation = "UPDATE", description = "恢复默认提示词模板")
  public Result<Void> resetPrompt(@PathVariable String key) {
    promptTemplateService.resetTemplate(key);
    return Result.success();
  }

  private PromptTemplateVO toPromptVO(AiPromptTemplate template) {
    PromptTemplateVO vo = new PromptTemplateVO();
    vo.setTemplateKey(template.getTemplateKey());
    vo.setName(template.getName());
    vo.setDescription(template.getDescription());
    vo.setSystemPrompt(template.getSystemPrompt());
    vo.setUserPromptTemplate(template.getUserPromptTemplate());
    vo.setVariables(template.getVariables());
    vo.setIsCustomized(template.getIsCustomized());
    return vo;
  }
}
