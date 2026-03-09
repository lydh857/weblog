package com.blog.interaction.service;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.ai.annotation.AiFeature;
import com.blog.infra.ai.service.AiClientService;
import com.blog.infra.ai.service.PromptTemplateService;
import com.blog.infra.ai.service.PromptTemplateService.RenderedPrompt;
import com.blog.interaction.entity.AiCommentReview;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.mapper.AiCommentReviewMapper;
import com.blog.interaction.mapper.CommentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI 评论审核服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

  private final AiClientService aiClient;
  private final PromptTemplateService promptTemplate;
  private final CommentMapper commentMapper;
  private final AiCommentReviewMapper reviewMapper;
  private final SensitiveWordService sensitiveWordService;
  private final ObjectMapper objectMapper;
  private final com.blog.infra.ai.config.AiProperties aiProperties;
  @org.springframework.beans.factory.annotation.Qualifier("aiExecutor")
  private final java.util.concurrent.Executor aiExecutor;

  private static final String FEATURE = "commentReview";

  /**
   * 异步审核单条评论
   */
  @Async
  @AiFeature("commentReview")
  public void reviewComment(Long commentId, String postTitle) {
    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) {
      log.warn("评论不存在，跳过审核: commentId={}", commentId);
      return;
    }

    try {
      doReview(comment, postTitle);
    } catch (BusinessException e) {
      // AI 功能不可用，回退到敏感词过滤
      log.warn("AI 审核不可用，回退敏感词过滤: {}", e.getMessage());
      fallbackToSensitiveWordFilter(comment);
    } catch (Exception e) {
      log.error("AI 审核异常，回退敏感词过滤: commentId={}", commentId, e);
      fallbackToSensitiveWordFilter(comment);
    }
  }

  /**
   * 批量异步审核（并行处理，每条评论独立审核，使用 AI 专用线程池）
   */
  @Async
  @AiFeature("commentReview")
  public void batchReview(List<Long> commentIds, String postTitle) {
    List<java.util.concurrent.CompletableFuture<Void>> futures = commentIds.stream()
      .map(commentId -> java.util.concurrent.CompletableFuture.runAsync(() -> {
        try {
          Comment comment = commentMapper.selectById(commentId);
          if (comment != null) {
            doReview(comment, postTitle);
          }
        } catch (Exception e) {
          log.error("批量审核异常: commentId={}", commentId, e);
        }
      }, aiExecutor))
      .toList();

    java.util.concurrent.CompletableFuture.allOf(
      futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
  }

  private void doReview(Comment comment, String postTitle) {
    RenderedPrompt prompt = promptTemplate.render("comment_review", Map.of(
      "commentContent", comment.getContent(),
      "postTitle", postTitle != null ? postTitle : ""
    ));

    String response = aiClient.call(FEATURE, prompt.systemPrompt(), prompt.userPrompt());
    parseAndSaveResult(comment, response);
  }

  private void parseAndSaveResult(Comment comment, String response) {
    try {
      String json = extractJson(response);
      JsonNode root = objectMapper.readTree(json);

      String result = root.has("result") ? root.get("result").asText("suspect") : "suspect";
      String reason = root.has("reason") ? root.get("reason").asText("") : "";
      BigDecimal confidence = root.has("confidence")
        ? BigDecimal.valueOf(root.get("confidence").asDouble(0.5))
        : BigDecimal.valueOf(0.5);

      // 校验 result 枚举值
      if (!List.of("pass", "suspect", "reject").contains(result)) {
        result = "suspect";
      }

      // 保存审核记录
      AiCommentReview review = new AiCommentReview();
      review.setCommentId(comment.getId());
      review.setResult(result);
      review.setReason(reason);
      review.setConfidence(confidence);
      review.setModel(aiProperties.getModel());
      // 估算 token 用量：输入（评论内容 + 提示词）+ 输出（JSON 响应）
      int estimatedTokens = (comment.getContent().length() + response.length()) / 4 + 1;
      review.setTokenUsed(estimatedTokens);
      reviewMapper.insert(review);

      // 更新评论的 AI 审核状态
      comment.setAiReviewStatus(result);
      comment.setAiReviewReason(reason);

      // reject 结果自动隐藏评论
      if ("reject".equals(result)) {
        comment.setStatus("rejected");
      }

      commentMapper.updateById(comment);
      log.info("AI 审核完成: commentId={}, result={}, confidence={}", comment.getId(), result, confidence);

    } catch (Exception e) {
      log.error("解析审核结果失败: commentId={}, response={}", comment.getId(), response, e);
      // 解析失败标记为 suspect
      comment.setAiReviewStatus("suspect");
      comment.setAiReviewReason("AI 返回格式异常");
      commentMapper.updateById(comment);
    }
  }

  /**
   * 回退到敏感词过滤
   */
  private void fallbackToSensitiveWordFilter(Comment comment) {
    String filtered = sensitiveWordService.filter(comment.getContent());
    if (!filtered.equals(comment.getContent())) {
      comment.setAiReviewStatus("suspect");
      comment.setAiReviewReason("敏感词过滤检测到违规内容");
    } else {
      comment.setAiReviewStatus("pass");
      comment.setAiReviewReason("敏感词过滤通过（AI 不可用）");
    }
    commentMapper.updateById(comment);
  }

  private String extractJson(String response) {
    if (response == null) return "{}";
    String trimmed = response.trim();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastFence > firstNewline) {
        trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
      }
    }
    return trimmed;
  }
}
