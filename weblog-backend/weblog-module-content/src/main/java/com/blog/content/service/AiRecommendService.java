package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.content.entity.PostEmbedding;
import com.blog.content.mapper.PostEmbeddingMapper;
import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.service.AiClientService;
import com.blog.infra.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 语义推荐服务
 * <p>
 * 优化：启动时预加载所有向量到内存缓存，避免每次查询全表扫描
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendService {

  private final AiClientService aiClient;
  private final PostEmbeddingMapper embeddingMapper;
  private final StringRedisTemplate redisTemplate;
  private final AiConfigService aiConfigService;
  private final AiProperties aiProperties;

  private static final String FEATURE = "recommend";
  private static final int DEFAULT_LIMIT = 6;

  /** 内存向量缓存：postId → float[] */
  private final ConcurrentHashMap<Long, float[]> vectorCache = new ConcurrentHashMap<>();

  /**
   * 启动时分页预加载所有向量到内存，避免一次性全量加载导致 OOM
   */
  @EventListener(ApplicationReadyEvent.class)
  public void preloadVectors() {
    try {
      int pageSize = 500;
      long lastId = 0;
      int totalLoaded = 0;

      while (true) {
        List<PostEmbedding> batch = embeddingMapper.selectList(
          new LambdaQueryWrapper<PostEmbedding>()
            .gt(PostEmbedding::getId, lastId)
            .orderByAsc(PostEmbedding::getId)
            .last("LIMIT " + pageSize));

        if (batch.isEmpty()) break;

        for (PostEmbedding pe : batch) {
          vectorCache.put(pe.getPostId(), bytesToFloatArray(pe.getEmbedding()));
          lastId = pe.getId();
        }
        totalLoaded += batch.size();
      }

      log.info("向量缓存预加载完成: {} 篇文章", totalLoaded);
    } catch (Exception e) {
      log.warn("向量缓存预加载失败，将在查询时按需加载: {}", e.getMessage());
    }
  }

  /**
   * 生成文章 Embedding 并存储，同时更新内存缓存
   * <p>
   * 注意：不使用 @AiFeature 注解，因为此方法从 PostService 调用，
   * 如果 recommend 功能关闭不应阻断文章发布流程，而是静默跳过。
   */
  @Async
  public void generateEmbedding(Long postId, String content) {
    // 手动检查全局开关和功能开关，关闭时静默返回
    if (!aiProperties.isEnabled()) {
      log.debug("AI 全局开关已关闭，跳过 Embedding 生成: postId={}", postId);
      return;
    }
    if (!aiConfigService.isFeatureEnabled("recommend")) {
      log.debug("语义推荐功能已关闭，跳过 Embedding 生成: postId={}", postId);
      return;
    }
    try {
      String text = content.length() > 5000 ? content.substring(0, 5000) : content;
      float[] vector = aiClient.embed(FEATURE, text);
      byte[] bytes = floatArrayToBytes(vector);

      PostEmbedding existing = embeddingMapper.selectOne(
        new LambdaQueryWrapper<PostEmbedding>().eq(PostEmbedding::getPostId, postId));

      if (existing != null) {
        existing.setEmbedding(bytes);
        existing.setDimension(vector.length);
        embeddingMapper.updateById(existing);
      } else {
        PostEmbedding embedding = new PostEmbedding();
        embedding.setPostId(postId);
        embedding.setEmbedding(bytes);
        embedding.setDimension(vector.length);
        embedding.setModel("embedding");
        embeddingMapper.insert(embedding);
      }

      // 更新内存缓存
      vectorCache.put(postId, vector);
      log.info("Embedding 生成完成: postId={}, dimension={}", postId, vector.length);
    } catch (Exception e) {
      log.error("Embedding 生成失败: postId={}", postId, e);
    }
  }

  /**
   * 从内存缓存中移除指定文章的向量（文章删除时调用）
   */
  public void evictCache(Long postId) {
    vectorCache.remove(postId);
  }

  /**
   * 向量语义检索：从内存缓存中计算余弦相似度
   */
  public List<Long> searchByVector(String queryText, int limit) {
    if (!aiClient.isEmbeddingAvailable()) {
      return List.of();
    }
    if (limit <= 0) limit = DEFAULT_LIMIT;

    try {
      float[] queryVector = aiClient.embed(FEATURE, queryText);

      // 从内存缓存计算相似度（无需查 DB）
      List<Map.Entry<Long, Double>> scored = new ArrayList<>();
      for (Map.Entry<Long, float[]> entry : vectorCache.entrySet()) {
        float[] vec = entry.getValue();
        if (vec.length == queryVector.length) {
          double sim = cosineSimilarity(queryVector, vec);
          scored.add(Map.entry(entry.getKey(), sim));
        }
      }

      scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
      return scored.stream()
        .filter(e -> e.getValue() > 0.3)
        .limit(limit)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("向量语义检索失败，将回退到关键词检索: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * 计算余弦相似度
   */
  public double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length || a.length == 0) return 0.0;

    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }

    double denominator = Math.sqrt(normA) * Math.sqrt(normB);
    if (denominator == 0.0) return 0.0;
    return dotProduct / denominator;
  }

  // ========== 序列化工具 ==========

  private byte[] floatArrayToBytes(float[] array) {
    ByteBuffer buffer = ByteBuffer.allocate(array.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float v : array) {
      buffer.putFloat(v);
    }
    return buffer.array();
  }

  private float[] bytesToFloatArray(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    float[] array = new float[bytes.length / 4];
    for (int i = 0; i < array.length; i++) {
      array[i] = buffer.getFloat();
    }
    return array;
  }
}
