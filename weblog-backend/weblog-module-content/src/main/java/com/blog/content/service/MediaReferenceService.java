package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.content.dto.MediaVO;
import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.content.mapper.PostContentMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.util.MarkdownImageExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 媒体引用检测服务
 * 负责构建引用 URL 集合、判断资源引用状态、统计未引用资源数量
 */
@Slf4j
@Service
public class MediaReferenceService {

  private static final String CACHE_KEY = "media:referenced-urls";
  private static final String DETAIL_CACHE_KEY = "media:reference-details";
  private static final long CACHE_TTL_MINUTES = 5;

  private final PostMapper postMapper;
  private final PostContentMapper postContentMapper;
  private final OssResourceMapper ossResourceMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final OssResourceService ossResourceService;

  public MediaReferenceService(PostMapper postMapper,
                               PostContentMapper postContentMapper,
                               OssResourceMapper ossResourceMapper,
                               StringRedisTemplate redisTemplate,
                               OssResourceService ossResourceService) {
    this.postMapper = postMapper;
    this.postContentMapper = postContentMapper;
    this.ossResourceMapper = ossResourceMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = new ObjectMapper();
    this.ossResourceService = ossResourceService;
  }

  /**
   * 获取所有被引用的 URL 集合
   * 优先从 Redis 缓存读取，未命中时查询数据库构建并缓存
   */
  public Set<String> getReferencedUrls() {
    // 尝试从 Redis 获取缓存
    try {
      String cached = redisTemplate.opsForValue().get(CACHE_KEY);
      if (cached != null) {
        Set<String> urls = objectMapper.readValue(cached, new TypeReference<Set<String>>() {});
        log.debug("从 Redis 缓存获取引用 URL 集合，数量: {}", urls.size());
        return urls;
      }
    } catch (Exception e) {
      log.warn("Redis 缓存读取失败，降级为数据库查询: {}", e.getMessage());
    }

    // 缓存未命中，从数据库构建
    Set<String> referencedUrls = buildReferencedUrlsFromDb();

    // 写入 Redis 缓存
    try {
      String json = objectMapper.writeValueAsString(referencedUrls);
      redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
      log.debug("引用 URL 集合已写入 Redis 缓存，数量: {}", referencedUrls.size());
    } catch (Exception e) {
      log.warn("Redis 缓存写入失败: {}", e.getMessage());
    }

    return referencedUrls;
  }

  /**
   * 判断指定 URL 是否被引用
   */
  public boolean isReferenced(String url) {
    if (url == null || url.isEmpty()) {
      return false;
    }
    return getReferencedUrls().contains(url);
  }

  /**
   * 统计未引用且非 avatar 类型的资源数量
   */
  public long countUnreferenced() {
    Set<String> referencedUrls = getReferencedUrls();

    // 查询所有非 avatar 类型的资源
    List<OssResource> resources = ossResourceMapper.selectList(
        new LambdaQueryWrapper<OssResource>()
            .ne(OssResource::getUsageType, "avatar")
            .select(OssResource::getUrl));

    return resources.stream()
        .filter(r -> r.getUrl() != null && !referencedUrls.contains(r.getUrl()))
        .count();
  }

  /**
   * 删除 Redis 缓存
   */
  public void evictCache() {
    try {
      redisTemplate.delete(CACHE_KEY);
      redisTemplate.delete(DETAIL_CACHE_KEY);
      log.debug("引用 URL 缓存已清除");
    } catch (Exception e) {
      log.warn("Redis 缓存删除失败: {}", e.getMessage());
    }
  }

  /**
   * 批量清理未引用资源，返回删除数量
   * 排除 avatar 类型资源，清理完成后自动失效缓存
   *
   * @param operatorId 操作者 ID
   * @return 删除的资源数量
   */
  public int cleanupUnreferenced(Long operatorId) {
    // 1. 获取引用 URL 集合
    Set<String> referencedUrls = getReferencedUrls();

    // 2. 查询所有非 avatar 类型的资源
    List<OssResource> resources = ossResourceMapper.selectList(
        new LambdaQueryWrapper<OssResource>()
            .ne(OssResource::getUsageType, "avatar")
            .select(OssResource::getId, OssResource::getUrl));

    // 3. 筛选出未引用的资源 ID 列表
    List<Long> unreferencedIds = resources.stream()
        .filter(r -> r.getUrl() != null && !referencedUrls.contains(r.getUrl()))
        .map(OssResource::getId)
        .toList();

    if (unreferencedIds.isEmpty()) {
      log.info("无未引用资源需要清理, operator={}", operatorId);
      return 0;
    }

    // 4. 调用批量删除
    int deletedCount = ossResourceService.batchDeleteByIds(unreferencedIds);
    log.info("未引用资源清理完成: 目标={}, 删除={}, operator={}", unreferencedIds.size(), deletedCount, operatorId);

    // 5. 失效缓存
    evictCache();

    return deletedCount;
  }

  /**
   * 从数据库构建引用 URL 集合
   */
  private Set<String> buildReferencedUrlsFromDb() {
    Set<String> urls = new HashSet<>();

    // 1. 获取所有未删除文章的封面图 URL
    List<String> coverImages = postMapper.selectAllCoverImages();
    if (coverImages != null) {
      for (String coverImage : coverImages) {
        if (coverImage != null && !coverImage.isEmpty()) {
          urls.add(coverImage);
        }
      }
    }

    // 2. 获取所有未删除文章的 Markdown 内容，提取图片 URL
    List<Long> activePostIds = postMapper.selectActiveIds();
    if (activePostIds != null && !activePostIds.isEmpty()) {
      List<String> contents = postContentMapper.selectContentsByPostIds(activePostIds);
      if (contents != null) {
        for (String content : contents) {
          urls.addAll(MarkdownImageExtractor.extractImageUrls(content));
        }
      }
    }

    log.info("从数据库构建引用 URL 集合完成，数量: {}", urls.size());
    return urls;
  }

  /**
   * 获取引用详情映射：URL -> 引用来源列表
   * 带 Redis 缓存（TTL 5 分钟）
   */
  public Map<String, List<MediaVO.ReferenceDetail>> getReferenceDetailsMap() {
    // 尝试从 Redis 获取缓存
    try {
      String cached = redisTemplate.opsForValue().get(DETAIL_CACHE_KEY);
      if (cached != null) {
        return objectMapper.readValue(cached,
            new TypeReference<Map<String, List<MediaVO.ReferenceDetail>>>() {});
      }
    } catch (Exception e) {
      log.warn("引用详情缓存读取失败，降级为数据库查询: {}", e.getMessage());
    }

    Map<String, List<MediaVO.ReferenceDetail>> detailMap = buildReferenceDetailsFromDb();

    // 写入缓存
    try {
      String json = objectMapper.writeValueAsString(detailMap);
      redisTemplate.opsForValue().set(DETAIL_CACHE_KEY, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    } catch (Exception e) {
      log.warn("引用详情缓存写入失败: {}", e.getMessage());
    }

    return detailMap;
  }

  /**
   * 从数据库构建引用详情映射
   */
  private Map<String, List<MediaVO.ReferenceDetail>> buildReferenceDetailsFromDb() {
    Map<String, List<MediaVO.ReferenceDetail>> detailMap = new HashMap<>();

    // 查询所有未删除文章的摘要信息
    List<Map<String, Object>> postSummaries = postMapper.selectActivePostSummaries();
    if (postSummaries == null || postSummaries.isEmpty()) {
      return detailMap;
    }

    // 构建 id -> title 映射
    Map<Long, String> idToTitle = new HashMap<>();
    for (Map<String, Object> row : postSummaries) {
      Long postId = ((Number) row.get("id")).longValue();
      String title = (String) row.get("title");
      idToTitle.put(postId, title != null ? title : "无标题");

      // 处理封面图引用
      String coverImage = (String) row.get("cover_image");
      if (coverImage != null && !coverImage.isEmpty()) {
        MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
        detail.setPostId(postId);
        detail.setPostTitle(idToTitle.get(postId));
        detail.setRefType("cover");
        detailMap.computeIfAbsent(coverImage, k -> new ArrayList<>()).add(detail);
      }
    }

    // 处理内容图引用
    List<Long> activeIds = new ArrayList<>(idToTitle.keySet());
    if (!activeIds.isEmpty()) {
      List<Map<String, Object>> contentRows = postContentMapper.selectIdAndContentByPostIds(activeIds);
      if (contentRows != null) {
        for (Map<String, Object> row : contentRows) {
          Long postId = ((Number) row.get("id")).longValue();
          String content = (String) row.get("content");
          Set<String> imageUrls = MarkdownImageExtractor.extractImageUrls(content);
          for (String url : imageUrls) {
            MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
            detail.setPostId(postId);
            detail.setPostTitle(idToTitle.get(postId));
            detail.setRefType("content");
            detailMap.computeIfAbsent(url, k -> new ArrayList<>()).add(detail);
          }
        }
      }
    }

    log.info("从数据库构建引用详情完成，URL 数量: {}", detailMap.size());
    return detailMap;
  }
}
