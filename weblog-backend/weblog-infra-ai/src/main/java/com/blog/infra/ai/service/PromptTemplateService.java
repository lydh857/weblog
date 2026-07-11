package com.blog.infra.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.ai.entity.AiPromptTemplate;
import com.blog.infra.ai.mapper.AiPromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板管理服务
 * <p>
 * 优化：使用内存缓存避免每次调用都查 DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

  private final AiPromptTemplateMapper promptTemplateMapper;

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

  /** 模板内存缓存：templateKey → AiPromptTemplate */
  private final ConcurrentHashMap<String, AiPromptTemplate> templateCache = new ConcurrentHashMap<>();

  /**
   * 获取模板：优先内存缓存 → 数据库 → classpath 默认模板
   */
  public AiPromptTemplate getTemplate(String templateKey) {
    // 优先内存缓存
    AiPromptTemplate cached = templateCache.get(templateKey);
    if (cached != null) {
      return cached;
    }

    // 查数据库
    AiPromptTemplate template = promptTemplateMapper.selectOne(
      new LambdaQueryWrapper<AiPromptTemplate>()
        .eq(AiPromptTemplate::getTemplateKey, templateKey)
    );
    if (template != null) {
      templateCache.put(templateKey, template);
      return template;
    }

    // 回退到 classpath 默认模板
    AiPromptTemplate defaultTemplate = loadDefaultTemplate(templateKey);
    templateCache.put(templateKey, defaultTemplate);
    return defaultTemplate;
  }

  /**
   * 更新模板，标记 isCustomized = true，并清除缓存
   */
  public void updateTemplate(String templateKey, String systemPrompt, String userPromptTemplate) {
    AiPromptTemplate template = promptTemplateMapper.selectOne(
      new LambdaQueryWrapper<AiPromptTemplate>()
        .eq(AiPromptTemplate::getTemplateKey, templateKey)
    );
    if (template == null) {
      throw new BusinessException(ResultCode.NOT_FOUND, "模板不存在: " + templateKey);
    }
    template.setSystemPrompt(systemPrompt);
    template.setUserPromptTemplate(userPromptTemplate);
    template.setIsCustomized(true);
    promptTemplateMapper.updateById(template);
    templateCache.remove(templateKey);
  }

  /**
   * 恢复默认：从 classpath 加载默认内容覆盖，并清除缓存
   */
  public void resetTemplate(String templateKey) {
    AiPromptTemplate template = promptTemplateMapper.selectOne(
      new LambdaQueryWrapper<AiPromptTemplate>()
        .eq(AiPromptTemplate::getTemplateKey, templateKey)
    );
    if (template == null) {
      throw new BusinessException(ResultCode.NOT_FOUND, "模板不存在: " + templateKey);
    }

    // 从 classpath 加载默认内容覆盖
    AiPromptTemplate defaultTemplate = loadDefaultTemplate(templateKey);
    template.setSystemPrompt(defaultTemplate.getSystemPrompt());
    template.setUserPromptTemplate(defaultTemplate.getUserPromptTemplate());
    template.setIsCustomized(false);
    promptTemplateMapper.updateById(template);
    templateCache.remove(templateKey);
  }

  /**
   * 渲染模板：加载模板并替换 {{xxx}} 占位符
   */
  public RenderedPrompt render(String templateKey, Map<String, String> variables) {
    AiPromptTemplate template = getTemplate(templateKey);
    String systemPrompt = replacePlaceholders(template.getSystemPrompt(), variables);
    String userPrompt = replacePlaceholders(template.getUserPromptTemplate(), variables);
    return new RenderedPrompt(systemPrompt, userPrompt);
  }

  /**
   * 替换模板中的 {{xxx}} 占位符
   */
  private String replacePlaceholders(String text, Map<String, String> variables) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (matcher.find()) {
      String key = matcher.group(1);
      String value = variables.getOrDefault(key, "");
      matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * 从 classpath 加载默认模板文件
   * 文件路径: prompts/{templateKey}_system.txt 和 prompts/{templateKey}_user.txt
   */
  private AiPromptTemplate loadDefaultTemplate(String templateKey) {
    AiPromptTemplate template = new AiPromptTemplate();
    template.setTemplateKey(templateKey);
    template.setSystemPrompt(loadClasspathFile("prompts/" + templateKey + "_system.txt"));
    template.setUserPromptTemplate(loadClasspathFile("prompts/" + templateKey + "_user.txt"));
    template.setIsCustomized(false);
    return template;
  }

  private String loadClasspathFile(String path) {
    try {
      ClassPathResource resource = new ClassPathResource(path);
      if (!resource.exists()) {
        log.warn("默认模板文件不存在: {}", path);
        return "";
      }
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("读取默认模板文件失败: {}", path, e);
      return "";
    }
  }

  /**
   * 渲染后的提示词
   */
  public record RenderedPrompt(String systemPrompt, String userPrompt) {
  }
}
