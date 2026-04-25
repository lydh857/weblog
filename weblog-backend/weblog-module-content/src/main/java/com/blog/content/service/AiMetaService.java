package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.*;
import com.blog.content.entity.Category;
import com.blog.content.entity.Tag;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.mapper.TagMapper;
import com.blog.infra.ai.annotation.AiFeature;
import com.blog.infra.ai.service.AiClientService;
import com.blog.infra.ai.service.PromptTemplateService;
import com.blog.infra.ai.service.PromptTemplateService.RenderedPrompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 元信息生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMetaService {

  private final AiClientService aiClient;
  private final PromptTemplateService promptTemplate;
  private final TagMapper tagMapper;
  private final CategoryMapper categoryMapper;
  private final PostMapper postMapper;
  private final ObjectMapper objectMapper;

  private static final String FEATURE = "meta";
  private static final int MIN_CONTENT_LENGTH = 100;

  /**
   * 一键生成全部元信息
   */
  @AiFeature("meta")
  public AiMetaResultVO generateAll(String title, String content) {
    checkContentLength(content);

    String existingTags = getExistingTagNames();
    String existingCategories = getExistingCategoryNames();

    RenderedPrompt prompt = promptTemplate.render("meta_all", Map.of(
      "title", title,
      "content", truncateContent(content),
      "existingTags", existingTags,
      "existingCategories", existingCategories
    ));

    String response = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    return parseMetaResult(response);
  }

  /**
   * 重新生成摘要（返回纯文本，去除 markdown 符号）
   */
  @AiFeature("meta")
  public String regenerateSummary(String title, String content) {
    checkContentLength(content);
    RenderedPrompt prompt = promptTemplate.render("meta_summary", Map.of(
      "title", title, "content", truncateContent(content)));
    String raw = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    return stripMarkdown(raw);
  }

  /**
   * 重新生成 SEO
   */
  @AiFeature("meta")
  public SeoResultVO regenerateSeo(String title, String content) {
    checkContentLength(content);
    RenderedPrompt prompt = promptTemplate.render("meta_seo", Map.of(
      "title", title, "content", truncateContent(content)));
    String response = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    return parseSeoResult(response);
  }

  /**
   * 重新生成标签推荐
   */
  @AiFeature("meta")
  public List<TagSuggestion> regenerateTags(String title, String content) {
    checkContentLength(content);
    String existingTags = getExistingTagNames();
    RenderedPrompt prompt = promptTemplate.render("meta_tags", Map.of(
      "title", title, "content", truncateContent(content), "existingTags", existingTags));
    String response = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    return parseTagSuggestions(response);
  }

  /**
   * 重新生成分类推荐
   */
  @AiFeature("meta")
  public List<CategorySuggestion> regenerateCategories(String title, String content) {
    checkContentLength(content);
    String existingCategories = getExistingCategoryNames();
    RenderedPrompt prompt = promptTemplate.render("meta_categories", Map.of(
      "title", title, "content", truncateContent(content), "existingCategories", existingCategories));
    String response = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    return parseCategorySuggestions(response);
  }

  /**
   * 生成 Slug，检测唯一性，冲突时追加数字后缀
   */
  @AiFeature("meta")
  public String regenerateSlug(String title) {
    RenderedPrompt prompt = promptTemplate.render("meta_slug", Map.of("title", title));
    String slug = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt())
      .trim().toLowerCase().replaceAll("[^a-z0-9-]", "");

    // 确保 slug 格式合规
    slug = normalizeSlug(slug);

    // 检测唯一性
    String baseSlug = slug;
    int suffix = 1;
    while (postMapper.countBySlugIncludeDeleted(slug) > 0) {
      slug = baseSlug + "-" + suffix;
      suffix++;
    }
    return slug;
  }

  // ========== 私有方法 ==========

  private void checkContentLength(String content) {
    if (content == null || content.length() < MIN_CONTENT_LENGTH) {
      throw new BusinessException(ResultCode.AI_CONTENT_TOO_SHORT);
    }
  }

  /** 截断过长内容，避免 token 浪费 */
  private String truncateContent(String content) {
    int maxLen = 5000;
    return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
  }

  private String getExistingTagNames() {
    List<Tag> tags = tagMapper.selectList(null);
    return tags.stream().map(Tag::getName).collect(Collectors.joining(", "));
  }

  private String getExistingCategoryNames() {
    List<Category> categories = categoryMapper.selectList(null);
    // 构建带层级的分类信息：「父分类/子分类」格式
    Map<Long, String> idNameMap = categories.stream()
      .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    return categories.stream().map(c -> {
      if (c.getParentId() != null && c.getParentId() != 0) {
        String parentName = idNameMap.get(c.getParentId());
        return parentName != null ? parentName + "/" + c.getName() : c.getName();
      }
      return c.getName();
    }).collect(Collectors.joining(", "));
  }

  /** 规范化 slug：去除首尾连字符、合并连续连字符、限制长度 */
  private String normalizeSlug(String slug) {
    if (slug == null || slug.isEmpty()) {
      return "untitled";
    }
    slug = slug.replaceAll("-{2,}", "-");
    slug = slug.replaceAll("^-|-$", "");
    if (slug.length() > 80) {
      slug = slug.substring(0, 80);
      slug = slug.replaceAll("-$", "");
    }
    return slug.isEmpty() ? "untitled" : slug;
  }

  /** 从 LLM 响应中提取 JSON（去除思考标签和 markdown 代码块标记） */
  private String extractJson(String response) {
    if (response == null) return "{}";
    String trimmed = response.trim();

    // 去除 Qwen3 等模型的 <think>...</think> 思考标签
    trimmed = trimmed.replaceAll("(?s)<think>.*?</think>", "").trim();

    // 去除 ```json ... ``` 包裹
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastFence > firstNewline) {
        trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
      }
    }

    // 兜底：尝试提取第一个 { 到最后一个 } 或第一个 [ 到最后一个 ] 之间的内容
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
      int objStart = trimmed.indexOf('{');
      int objEnd = trimmed.lastIndexOf('}');
      int arrStart = trimmed.indexOf('[');
      int arrEnd = trimmed.lastIndexOf(']');

      boolean hasObj = objStart >= 0 && objEnd > objStart;
      boolean hasArr = arrStart >= 0 && arrEnd > arrStart;

      if (hasArr && (!hasObj || arrStart < objStart)) {
        // 数组在前，优先提取数组
        trimmed = trimmed.substring(arrStart, arrEnd + 1);
      } else if (hasObj) {
        trimmed = trimmed.substring(objStart, objEnd + 1);
      }
    }

    return trimmed;
  }

  private AiMetaResultVO parseMetaResult(String response) {
    try {
      String json = extractJson(response);
      log.debug("AI 原始响应（前500字符）: {}", response != null && response.length() > 500 ? response.substring(0, 500) : response);
      log.debug("提取后 JSON: {}", json);
      JsonNode root = objectMapper.readTree(json);
      AiMetaResultVO vo = new AiMetaResultVO();
      // 兼容 AI 可能返回的变体字段名
      vo.setSummary(stripMarkdown(getTextWithFallback(root, "summary", "excerpt", "description")));
      vo.setSeoTitle(getTextWithFallback(root, "seoTitle", "seo_title"));
      vo.setSeoDescription(stripMarkdown(getTextWithFallback(root, "seoDescription", "seo_description")));
      vo.setSeoKeywords(parseStringList(getNodeWithFallback(root, "seoKeywords", "seo_keywords", "keywords")));
      vo.setTags(parseTagNodes(getNodeWithFallback(root, "tags")));
      vo.setCategories(parseCategoryNodes(getNodeWithFallback(root, "categories", "category")));
      vo.setSlug(normalizeSlug(getTextWithFallback(root, "slug")));
      return vo;
    } catch (JsonProcessingException e) {
      log.error("解析元信息 JSON 失败: {}", response, e);
      throw new BusinessException(ResultCode.AI_API_ERROR, "AI 返回格式异常");
    }
  }

  private SeoResultVO parseSeoResult(String response) {
    try {
      String json = extractJson(response);
      JsonNode root = objectMapper.readTree(json);
      SeoResultVO vo = new SeoResultVO();
      vo.setSeoTitle(getTextWithFallback(root, "seoTitle", "seo_title"));
      vo.setSeoDescription(getTextWithFallback(root, "seoDescription", "seo_description", "description"));
      vo.setSeoKeywords(parseStringList(getNodeWithFallback(root, "seoKeywords", "seo_keywords", "keywords")));
      return vo;
    } catch (JsonProcessingException e) {
      log.error("解析 SEO JSON 失败: {}", response, e);
      throw new BusinessException(ResultCode.AI_API_ERROR, "AI 返回格式异常");
    }
  }

  private List<TagSuggestion> parseTagSuggestions(String response) {
    try {
      String json = extractJson(response);
      JsonNode root = objectMapper.readTree(json);
      return parseTagNodes(root);
    } catch (JsonProcessingException e) {
      log.error("解析标签 JSON 失败: {}", response, e);
      throw new BusinessException(ResultCode.AI_API_ERROR, "AI 返回格式异常");
    }
  }

  private List<CategorySuggestion> parseCategorySuggestions(String response) {
    try {
      String json = extractJson(response);
      JsonNode root = objectMapper.readTree(json);
      return parseCategoryNodes(root);
    } catch (JsonProcessingException e) {
      log.error("解析分类 JSON 失败: {}", response, e);
      throw new BusinessException(ResultCode.AI_API_ERROR, "AI 返回格式异常");
    }
  }

  private List<TagSuggestion> parseTagNodes(JsonNode node) {
    List<TagSuggestion> result = new ArrayList<>();
    if (node == null || !node.isArray()) return result;

    // 查询已有标签用于匹配
    Map<String, Long> existingTagMap = tagMapper.selectList(null).stream()
      .collect(Collectors.toMap(t -> t.getName().toLowerCase(), Tag::getId, (a, b) -> a));

    for (JsonNode item : node) {
      TagSuggestion tag = new TagSuggestion();
      // 兼容字符串数组 ["标签1"] 和对象数组 [{"name":"标签1"}]
      String name = item.isTextual() ? item.asText() : getTextOrEmpty(item, "name");
      if (name.isEmpty()) continue;
      tag.setName(name);
      Long existingId = existingTagMap.get(name.toLowerCase());
      tag.setIsExisting(existingId != null);
      tag.setTagId(existingId);
      result.add(tag);
    }

    // 已有标签排在前面
    result.sort((a, b) -> Boolean.compare(b.getIsExisting(), a.getIsExisting()));
    return result;
  }

  private List<CategorySuggestion> parseCategoryNodes(JsonNode node) {
    List<CategorySuggestion> result = new ArrayList<>();
    if (node == null) return result;

    // 兼容单字符串值 "category": "技术" → 转为列表
    if (node.isTextual()) {
      String name = node.asText().trim();
      if (!name.isEmpty()) {
        CategorySuggestion cat = new CategorySuggestion();
        cat.setName(name);
        Map<String, Long> existingCatMap = categoryMapper.selectList(null).stream()
          .collect(Collectors.toMap(c -> c.getName().toLowerCase(), Category::getId, (a, b) -> a));
        Long existingId = existingCatMap.get(name.toLowerCase());
        cat.setIsExisting(existingId != null);
        cat.setCategoryId(existingId);
        cat.setParentName("");
        result.add(cat);
      }
      return result;
    }

    if (!node.isArray()) return result;

    List<Category> allCategories = categoryMapper.selectList(null);
    Map<String, Category> catByNameLower = allCategories.stream()
      .collect(Collectors.toMap(c -> c.getName().toLowerCase(), c -> c, (a, b) -> a));
    Map<Long, String> idNameMap = allCategories.stream()
      .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

    for (JsonNode item : node) {
      CategorySuggestion cat = new CategorySuggestion();
      // 兼容字符串数组 ["分类1"] 和对象数组 [{"name":"分类1"}]
      String name = item.isTextual() ? item.asText() : getTextOrEmpty(item, "name");
      if (name.isEmpty()) continue;

      // 解析 parentName：AI 可能返回 parentName 字段，也可能返回 "父分类/子分类" 格式
      String parentName = item.isTextual() ? "" : getTextWithFallback(item, "parentName", "parent_name", "parent");
      if (parentName.isEmpty() && name.contains("/")) {
        String[] parts = name.split("/", 2);
        parentName = parts[0].trim();
        name = parts[1].trim();
      }

      cat.setName(name);
      cat.setParentName(parentName);

      // 匹配已有分类（考虑父子关系）
      Category existingCat = catByNameLower.get(name.toLowerCase());
      if (existingCat != null) {
        // 如果有 parentName，验证父分类是否匹配
        if (!parentName.isEmpty() && existingCat.getParentId() != null && existingCat.getParentId() != 0) {
          String actualParent = idNameMap.get(existingCat.getParentId());
          if (actualParent != null && actualParent.equalsIgnoreCase(parentName)) {
            cat.setIsExisting(true);
            cat.setCategoryId(existingCat.getId());
          } else {
            cat.setIsExisting(false);
            cat.setCategoryId(null);
          }
        } else {
          cat.setIsExisting(true);
          cat.setCategoryId(existingCat.getId());
        }
      } else {
        cat.setIsExisting(false);
        cat.setCategoryId(null);
      }

      result.add(cat);
    }
    return result;
  }

  private String getTextOrEmpty(JsonNode node, String field) {
    if (node == null || !node.has(field) || node.get(field).isNull()) return "";
    return node.get(field).asText("");
  }

  /** 按优先级尝试多个字段名，返回第一个非空值 */
  private String getTextWithFallback(JsonNode node, String... fields) {
    if (node == null) return "";
    for (String field : fields) {
      if (node.has(field) && !node.get(field).isNull()) {
        return node.get(field).asText("");
      }
    }
    return "";
  }

  /** 按优先级尝试多个字段名，返回第一个存在的节点 */
  private JsonNode getNodeWithFallback(JsonNode node, String... fields) {
    if (node == null) return null;
    for (String field : fields) {
      if (node.has(field) && !node.get(field).isNull()) {
        return node.get(field);
      }
    }
    return null;
  }

  private List<String> parseStringList(JsonNode node) {
    List<String> result = new ArrayList<>();
    if (node == null || !node.isArray()) return result;
    for (JsonNode item : node) {
      result.add(item.asText());
    }
    return result;
  }

  /**
   * 去除文本中的 Markdown 格式符号，返回纯文本
   */
  private String stripMarkdown(String text) {
    if (text == null || text.isBlank()) return text;
    return text
      .replaceAll("#{1,6}\\s+", "")          // 标题 # ## ###
      .replaceAll("\\*{1,2}([^*]+?)\\*{1,2}", "$1")  // 加粗/斜体 *text* **text**
      .replaceAll("_{1,2}([^_]+?)_{1,2}", "$1")     // 加粗/斜体 _text_ __text__
      .replaceAll("`{1,3}[^`]+?`{1,3}", "")   // 行内代码 `code` ```code```
      .replaceAll("~{2}[^~]+?~{2}", "")       // 删除线 ~~text~~
      .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")  // 链接 [text](url)
      .replaceAll("^[-*+]\\s+", "")            // 无序列表 - item / * item
      .replaceAll("\\n[-*+]\\s+", "\n")        // 多行无序列表
      .replaceAll(">\\s*", "")                  // 引用 > text
      .trim();
  }
}
