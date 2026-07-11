package com.blog.content.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blog.content.dto.MediaVO;
import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.content.mapper.PostContentMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.mapper.TopicMapper;
import com.blog.content.mapper.CarouselMapper;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.content.util.MarkdownImageExtractor;
import com.blog.infra.oss.StorageFacade;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
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
  private static final int CONTENT_PAGE_SIZE = 200;
  private static final java.util.regex.Pattern CSS_URL_PATTERN = java.util.regex.Pattern.compile(
      "url\\(\\s*['\"]?([^)'\"\\s]+)['\"]?\\s*\\)",
      java.util.regex.Pattern.CASE_INSENSITIVE);

  private final PostMapper postMapper;
  private final PostContentMapper postContentMapper;
  private final OssResourceMapper ossResourceMapper;
  private final TopicMapper topicMapper;
  private final CarouselMapper carouselMapper;
  private final FriendLinkMapper friendLinkMapper;
  private final AdvertisementMapper advertisementMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final OssResourceService ossResourceService;
  private final StorageFacade storageFacade;

  public MediaReferenceService(PostMapper postMapper,
                               PostContentMapper postContentMapper,
                               OssResourceMapper ossResourceMapper,
                               TopicMapper topicMapper,
                               CarouselMapper carouselMapper,
                               FriendLinkMapper friendLinkMapper,
                                AdvertisementMapper advertisementMapper,
                                StringRedisTemplate redisTemplate,
                                OssResourceService ossResourceService,
                                StorageFacade storageFacade) {
    this.postMapper = postMapper;
    this.postContentMapper = postContentMapper;
    this.ossResourceMapper = ossResourceMapper;
    this.topicMapper = topicMapper;
    this.carouselMapper = carouselMapper;
    this.friendLinkMapper = friendLinkMapper;
    this.advertisementMapper = advertisementMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = new ObjectMapper();
    this.ossResourceService = ossResourceService;
    this.storageFacade = storageFacade;
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
    return buildReferenceKeys(getReferencedUrls()).contains(toReferenceKey(url));
  }

  /**
   * 将 URL 转为引用比较 key，优先使用对象存储 key，兼容历史本地 /uploads/ 域名变化。
   */
  public String toReferenceKey(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    String trimmed = url.trim();
    String withoutQuery = stripQueryAndFragment(trimmed);
    try {
      String objectKey = storageFacade.extractObjectKey(withoutQuery);
      if (objectKey != null && !objectKey.isBlank()) {
        return "key:" + objectKey;
      }
    } catch (Exception e) {
      log.debug("对象 key 解析失败，降级 URL 归一化: {}", trimmed, e);
    }

    String uploadKey = extractLocalUploadKey(withoutQuery);
    if (uploadKey != null && !uploadKey.isBlank()) {
      return "key:" + uploadKey;
    }
    return "url:" + withoutQuery;
  }

  public Set<String> buildReferenceKeys(Collection<String> urls) {
    if (urls == null || urls.isEmpty()) {
      return Set.of();
    }
    Set<String> keys = new HashSet<>();
    for (String url : urls) {
      String key = toReferenceKey(url);
      if (key != null) {
        keys.add(key);
      }
    }
    return keys;
  }

  public Map<String, List<MediaVO.ReferenceDetail>> buildReferenceDetailsByKey(Map<String, List<MediaVO.ReferenceDetail>> detailMap) {
    Map<String, List<MediaVO.ReferenceDetail>> normalized = new HashMap<>();
    if (detailMap == null || detailMap.isEmpty()) {
      return normalized;
    }
    detailMap.forEach((url, details) -> {
      String key = toReferenceKey(url);
      if (key != null) {
        normalized.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(details);
      }
    });
    return normalized;
  }

    /**
     * 统计未引用资源数量
     */
  public long countUnreferenced() {
    return countUnreferenced(Set.of());
  }

  public long countUnreferenced(Set<String> extraReferencedUrls) {
    Set<String> referencedUrls = getReferencedUrls();
    Set<String> referencedKeys = buildReferenceKeys(mergeReferencedUrls(referencedUrls, extraReferencedUrls));

    // 查询所有资源
    List<OssResource> resources = ossResourceMapper.selectList(
        new QueryWrapper<OssResource>()
            .select("url"));

    return resources.stream()
        .filter(r -> r.getUrl() != null && !referencedKeys.contains(toReferenceKey(r.getUrl())))
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
   * 清理完成后自动失效缓存
   *
   * @param operatorId 操作者 ID
   * @return 删除的资源数量
   */
  public int cleanupUnreferenced(Long operatorId) {
    return cleanupUnreferenced(operatorId, Set.of());
  }

  public int cleanupUnreferenced(Long operatorId, Set<String> extraReferencedUrls) {
    // 1. 获取引用 URL 集合
    Set<String> referencedKeys = buildReferenceKeys(mergeReferencedUrls(getReferencedUrls(), extraReferencedUrls));

    // 2. 查询所有资源
    List<OssResource> resources = ossResourceMapper.selectList(
        new QueryWrapper<OssResource>()
            .select("id", "url"));

    // 3. 筛选出未引用的资源 ID 列表
    List<Long> unreferencedIds = resources.stream()
        .filter(r -> r.getUrl() != null && !referencedKeys.contains(toReferenceKey(r.getUrl())))
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

        // 2. 分页获取未删除文章的 Markdown 内容，提取图片 URL（避免一次性全量加载）
    long contentOffset = 0;
    while (true) {
      List<Map<String, Object>> rows = postContentMapper.selectActiveContentPageById(contentOffset, CONTENT_PAGE_SIZE);
      if (rows == null || rows.isEmpty()) {
        break;
      }
      for (Map<String, Object> row : rows) {
        String content = (String) row.get("content");
        if (content != null && !content.isEmpty()) {
          urls.addAll(MarkdownImageExtractor.extractImageUrls(content));
        }
        Object idVal = row.get("id");
        if (idVal instanceof Number num) {
          contentOffset = num.longValue();
        }
      }
      if (rows.size() < CONTENT_PAGE_SIZE) {
        break;
      }
    }

    // 3. 获取已发布专题封面 URL
    List<String> topicCovers = topicMapper.selectPublishedCoverUrls();
    if (topicCovers != null) {
      topicCovers.forEach(url -> addUrl(urls, url));
    }

    // 4. 获取所有轮播配置图片 URL，禁用轮播仍然持有图片引用，不能作为未引用资源清理。
    List<String> carouselImages = carouselMapper.selectConfiguredImageUrls();
    if (carouselImages != null) {
      carouselImages.forEach(url -> addUrl(urls, url));
    }

    // 5. 获取有效友链 Logo URL
    List<String> friendLinkLogos = friendLinkMapper.selectEffectiveLogoUrls();
    if (friendLinkLogos != null) {
      friendLinkLogos.forEach(url -> addUrl(urls, url));
    }

    // 6. 获取图片广告 URL
    List<String> adImages = advertisementMapper.selectImageContentUrls();
    if (adImages != null) {
      adImages.forEach(content -> addAdImageUrls(urls, content));
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

    // 构建 id -> title 映射
    Map<Long, String> idToTitle = new HashMap<>();
    if (postSummaries != null) {
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
    }

        // 处理内容图引用（分页查询，避免一次性加载全量内容）
    long contentOffset = 0;
    while (true) {
      List<Map<String, Object>> contentRows = postContentMapper.selectActiveContentPageById(contentOffset, CONTENT_PAGE_SIZE);
      if (contentRows == null || contentRows.isEmpty()) {
        break;
      }
      for (Map<String, Object> row : contentRows) {
        Long postId = ((Number) row.get("id")).longValue();
        String content = (String) row.get("content");
        // title 可能在 idToTitle 中不存在（如新文章在查询 summaries 后新增），跳过即可
        String postTitle = idToTitle.getOrDefault(postId, "无标题");
        Set<String> imageUrls = MarkdownImageExtractor.extractImageUrls(content);
        for (String url : imageUrls) {
          MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
          detail.setPostId(postId);
          detail.setPostTitle(postTitle);
          detail.setRefType("content");
          detailMap.computeIfAbsent(url, k -> new ArrayList<>()).add(detail);
        }
        contentOffset = postId;
      }
      if (contentRows.size() < CONTENT_PAGE_SIZE) {
        break;
      }
    }

    // 处理专题封面引用
    List<Map<String, Object>> topicSummaries = topicMapper.selectPublishedTopicSummaries();
    if (topicSummaries != null) {
      for (Map<String, Object> row : topicSummaries) {
        Long topicId = ((Number) row.get("id")).longValue();
        String title = (String) row.get("title");
        String cover = (String) row.get("cover");
        if (cover != null && !cover.isEmpty()) {
          MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
          detail.setPostId(topicId);
          detail.setPostTitle(title == null || title.isBlank() ? "无标题" : title);
          detail.setRefType("topic_cover");
          detailMap.computeIfAbsent(cover, k -> new ArrayList<>()).add(detail);
        }
      }
    }

    // 处理轮播图引用
    List<String> carouselImages = carouselMapper.selectConfiguredImageUrls();
    if (carouselImages != null) {
      for (int i = 0; i < carouselImages.size(); i++) {
        String imageUrl = carouselImages.get(i);
        if (imageUrl == null || imageUrl.isBlank()) continue;
        MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
        detail.setPostId(0L);
        detail.setPostTitle("第 " + (i + 1) + " 项");
        detail.setRefType("carousel_image");
        detailMap.computeIfAbsent(imageUrl, k -> new ArrayList<>()).add(detail);
      }
    }

    // 处理友链 Logo 引用
    List<String> friendLinkLogos = friendLinkMapper.selectEffectiveLogoUrls();
    if (friendLinkLogos != null) {
      for (int i = 0; i < friendLinkLogos.size(); i++) {
        String logo = friendLinkLogos.get(i);
        if (logo == null || logo.isBlank()) continue;
        MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
        detail.setPostId(0L);
        detail.setPostTitle("第 " + (i + 1) + " 项");
        detail.setRefType("friend_link_logo");
        detailMap.computeIfAbsent(logo, k -> new ArrayList<>()).add(detail);
      }
    }

    // 处理图片广告引用
    List<Map<String, Object>> adSummaries = advertisementMapper.selectImageAdSummaries();
    if (adSummaries != null) {
      for (Map<String, Object> row : adSummaries) {
        Long adId = ((Number) row.get("id")).longValue();
        String title = (String) row.get("title");
        String content = (String) row.get("content");
        String position = (String) row.get("position");
        Object insertAfter = row.get("insert_after");
        Set<String> imageUrls = extractAdImageUrls(content);
        for (String imageUrl : imageUrls) {
          MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
          detail.setPostId(adId);
          detail.setPostTitle(formatAdReferenceTitle(title, position, insertAfter));
          detail.setRefType("ad_image");
          detailMap.computeIfAbsent(imageUrl, k -> new ArrayList<>()).add(detail);
        }
      }
    }

    log.info("从数据库构建引用详情完成，URL 数量: {}", detailMap.size());
    return detailMap;
  }

  private void addUrl(Set<String> urls, String url) {
    if (url != null && !url.isEmpty()) {
      urls.add(url);
    }
  }

  private void addAdImageUrls(Set<String> urls, String content) {
    extractAdImageUrls(content).forEach(url -> addUrl(urls, url));
  }

  private Set<String> extractAdImageUrls(String content) {
    if (content == null || content.isBlank()) {
      return Set.of();
    }
    String trimmed = content.trim();
    Set<String> urls = new HashSet<>(MarkdownImageExtractor.extractImageUrls(trimmed));
    java.util.regex.Matcher cssUrlMatcher = CSS_URL_PATTERN.matcher(trimmed);
    while (cssUrlMatcher.find()) {
      String url = cssUrlMatcher.group(1);
      if (isDirectImageUrl(url)) {
        urls.add(url);
      }
    }
    if (isDirectImageUrl(trimmed)) {
      urls.add(trimmed);
    }
    return urls;
  }

  private boolean isDirectImageUrl(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return !lower.contains("<")
        && !lower.contains(" ")
        && (lower.startsWith("http://")
            || lower.startsWith("https://")
            || lower.startsWith("/uploads/")
            || lower.contains("/uploads/"));
  }

  private String formatAdReferenceTitle(String title, String position, Object insertAfter) {
    String safeTitle = title == null || title.isBlank() ? "未命名广告" : title;
    String safePosition = position == null || position.isBlank() ? "未知位置" : position;
    if (insertAfter instanceof Number number) {
      return safePosition + "，第 " + number.intValue() + " 篇后，" + safeTitle;
    }
    return safePosition + "，" + safeTitle;
  }

  private Set<String> mergeReferencedUrls(Set<String> referencedUrls, Set<String> extraReferencedUrls) {
    if (extraReferencedUrls == null || extraReferencedUrls.isEmpty()) {
      return referencedUrls;
    }
    Set<String> merged = new HashSet<>(referencedUrls);
    merged.addAll(extraReferencedUrls);
    return merged;
  }

  private String stripQueryAndFragment(String url) {
    int queryIndex = url.indexOf('?');
    int fragmentIndex = url.indexOf('#');
    int endIndex = url.length();
    if (queryIndex >= 0) {
      endIndex = Math.min(endIndex, queryIndex);
    }
    if (fragmentIndex >= 0) {
      endIndex = Math.min(endIndex, fragmentIndex);
    }
    return url.substring(0, endIndex);
  }

  private String extractLocalUploadKey(String url) {
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path != null) {
        return extractLocalUploadKeyFromPath(path);
      }
    } catch (Exception ignored) {
      // 非标准 URL 继续按原字符串兜底解析。
    }
    return extractLocalUploadKeyFromPath(url);
  }

  private String extractLocalUploadKeyFromPath(String path) {
    String marker = "/uploads/";
    int index = path.indexOf(marker);
    if (index < 0) {
      return null;
    }
    return path.substring(index + marker.length());
  }
}
