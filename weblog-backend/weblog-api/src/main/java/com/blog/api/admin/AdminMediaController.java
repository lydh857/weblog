package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.MediaStatsVO;
import com.blog.content.dto.MediaVO;
import com.blog.content.entity.OssResource;
import com.blog.content.service.MediaReferenceService;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.StorageFacade;
import com.blog.infra.security.audit.AuditLog;
import com.blog.system.mapper.UserMapper;
import com.blog.system.mapper.UserProfileReviewMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端媒体资源管理接口
 */
@Tag(name = "管理端-媒体管理", description = "OSS 资源列表、删除")
@RestController
@RequestMapping("/api/admin/media")
public class AdminMediaController {

  private static final String LEGACY_LOCAL_UPLOAD_PREFIX = "http://localhost:9091/uploads";

  @Autowired(required = false)
  private OssResourceService ossResourceService;

  @Autowired(required = false)
  private MediaReferenceService mediaReferenceService;

  @Autowired
  private StorageFacade storageFacade;

  @Autowired(required = false)
  private UserMapper userMapper;

  @Autowired(required = false)
  private UserProfileReviewMapper userProfileReviewMapper;

  @Value("${blog.upload.base-url:http://localhost:9091/uploads}")
  private String uploadBaseUrl;

  private void checkOssEnabled() {
    if (ossResourceService == null || !storageFacade.isStorageEnabled()) {
      throw new BusinessException(ResultCode.FAIL, "媒体存储服务未启用");
    }
  }

  /**
   * 分页查询媒体资源
   * 支持按 usageType 和 referenceStatus 筛选
   * referenceStatus 取值: referenced / unreferenced / null(全部)
   */
  @Operation(summary = "分页查询媒体资源")
  @GetMapping
  public Result<IPage<MediaVO>> list(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String usageType,
      @RequestParam(required = false) String referenceStatus) {
    checkOssEnabled();
    StpUtil.checkLogin();
    PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
    Long userId = StpUtil.getLoginIdAsLong();
    boolean isAdmin = StpUtil.hasRole("admin");
    Long uploaderId = isAdmin ? null : userId;

    // 获取引用 URL 集合和引用详情
    Set<String> referencedUrls = new java.util.HashSet<>(mediaReferenceService != null
        ? mediaReferenceService.getReferencedUrls()
        : Set.of());
    Map<String, List<MediaVO.ReferenceDetail>> detailMap = new java.util.HashMap<>(mediaReferenceService != null
        ? mediaReferenceService.getReferenceDetailsMap()
        : Map.of());

    appendAvatarReferences(referencedUrls, detailMap);

    Set<String> normalizedReferencedUrls = referencedUrls.stream()
        .map(this::normalizeLegacyUploadUrl)
        .collect(Collectors.toSet());
    Map<String, List<MediaVO.ReferenceDetail>> normalizedDetailMap = new HashMap<>();
    detailMap.forEach((rawUrl, details) -> {
      String normalizedUrl = normalizeLegacyUploadUrl(rawUrl);
      normalizedDetailMap.computeIfAbsent(normalizedUrl, k -> new ArrayList<>()).addAll(details);
    });

    IPage<OssResource> page;
    if (referenceStatus != null && !referenceStatus.isEmpty()) {
      // 按引用状态筛选：在数据库层过滤，避免内存加载
      page = ossResourceService.pageByReferenceStatus(
          pageParams.pageNum(), pageParams.pageSize(), uploaderId, usageType, referenceStatus, referencedUrls);
    } else {
      // 无引用状态筛选：正常分页查询
      page = ossResourceService.page(pageParams.pageNum(), pageParams.pageSize(), uploaderId, usageType);
    }

    IPage<MediaVO> voPage = page.convert(r -> toMediaVO(r, normalizedReferencedUrls, normalizedDetailMap));
    return Result.success(voPage);
  }

  @Operation(summary = "删除媒体资源（同步删除 OSS 文件）")
  @DeleteMapping("/{id}")
  @AuditLog(module = "媒体管理", operation = "DELETE", description = "删除媒体资源")
  public Result<Void> delete(@PathVariable Long id) {
    checkOssEnabled();
    StpUtil.checkLogin();
    Long userId = StpUtil.getLoginIdAsLong();
    boolean isAdmin = StpUtil.hasRole("admin");
    ossResourceService.delete(id, userId, isAdmin);
    return Result.success();
  }

  @Operation(summary = "获取媒体资源详情")
  @GetMapping("/{id}")
  public Result<OssResource> detail(@PathVariable Long id) {
    checkOssEnabled();
    StpUtil.checkLogin();
    OssResource resource = ossResourceService.getById(id);
    if (resource != null && resource.getUrl() != null) {
      resource.setUrl(normalizeLegacyUploadUrl(resource.getUrl()));
    }
    return Result.success(resource);
  }

  @Operation(summary = "获取未引用资源数量")
  @GetMapping("/unreferenced-count")
  public Result<Long> unreferencedCount() {
    checkOssEnabled();
    StpUtil.checkLogin();
    if (mediaReferenceService == null) {
      return Result.success(0L);
    }
    return Result.success(mediaReferenceService.countUnreferenced());
  }

  @Operation(summary = "获取媒体资源统计")
  @GetMapping("/stats")
  public Result<MediaStatsVO> stats() {
    checkOssEnabled();
    StpUtil.checkLogin();
    Long userId = StpUtil.getLoginIdAsLong();
    boolean isAdmin = StpUtil.hasRole("admin");
    Long uploaderId = isAdmin ? null : userId;
    return Result.success(ossResourceService.getStats(uploaderId));
  }

  @Operation(summary = "清理所有未引用资源（仅管理员）")
  @PostMapping("/cleanup-unreferenced")
  @AuditLog(module = "媒体管理", operation = "CLEANUP", description = "清理未引用媒体资源")
  public Result<Integer> cleanupUnreferenced() {
    checkOssEnabled();
    StpUtil.checkLogin();
    if (mediaReferenceService == null) {
      return Result.success(0);
    }
    Long operatorId = StpUtil.getLoginIdAsLong();
    int count = mediaReferenceService.cleanupUnreferenced(operatorId);
    return Result.success(count);
  }

  /** OssResource 转 MediaVO，设置 referenced 字段和引用详情 */
  private MediaVO toMediaVO(OssResource resource, Set<String> referencedUrls,
                            Map<String, List<MediaVO.ReferenceDetail>> detailMap) {
    String normalizedUrl = normalizeLegacyUploadUrl(resource.getUrl());
    MediaVO vo = new MediaVO();
    vo.setId(resource.getId());
    vo.setFileName(resource.getFileName());
    vo.setFilePath(resource.getFilePath());
    vo.setFileSize(resource.getFileSize());
    vo.setFileType(resource.getFileType());
    vo.setMimeType(resource.getMimeType());
    vo.setUrl(normalizedUrl);
    vo.setUploaderId(resource.getUploaderId());
    vo.setUsageType(resource.getUsageType());
    vo.setCreateTime(resource.getCreateTime());

    // 生成缩略图 URL：OSS 模式使用图片处理参数，本地模式用原始 URL
    if (storageFacade.supportsImageProcessing() && normalizedUrl != null) {
      String objectKey = storageFacade.extractObjectKey(normalizedUrl);
      if (objectKey != null) {
        vo.setThumbnailUrl(storageFacade.getThumbnailUrl(objectKey));
      } else {
        vo.setThumbnailUrl(normalizedUrl);
      }
    } else {
      vo.setThumbnailUrl(normalizedUrl);
    }

    vo.setReferenced(normalizedUrl != null && referencedUrls.contains(normalizedUrl));
    vo.setReferenceDetails(detailMap.getOrDefault(normalizedUrl, List.of()));
    return vo;
  }

  private void appendAvatarReferences(Set<String> referencedUrls, Map<String, List<MediaVO.ReferenceDetail>> detailMap) {
    if (userMapper != null) {
      List<String> avatarUrls = userMapper.selectActiveAvatarUrls();
      if (avatarUrls != null) {
        for (String avatarUrl : avatarUrls) {
          if (avatarUrl == null || avatarUrl.isBlank()) continue;
          referencedUrls.add(avatarUrl);
          MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
          detail.setPostId(0L);
          detail.setPostTitle("[用户] 已使用头像");
          detail.setRefType("user_avatar");
          detailMap.computeIfAbsent(avatarUrl, k -> new java.util.ArrayList<>()).add(detail);
        }
      }
    }

    if (userProfileReviewMapper != null) {
      List<String> pendingAvatarUrls = userProfileReviewMapper.selectPendingAvatarUrls();
      if (pendingAvatarUrls != null) {
        for (String avatarUrl : pendingAvatarUrls) {
          if (avatarUrl == null || avatarUrl.isBlank()) continue;
          referencedUrls.add(avatarUrl);
          MediaVO.ReferenceDetail detail = new MediaVO.ReferenceDetail();
          detail.setPostId(0L);
          detail.setPostTitle("[用户] 待审核头像");
          detail.setRefType("user_avatar_pending");
          detailMap.computeIfAbsent(avatarUrl, k -> new java.util.ArrayList<>()).add(detail);
        }
      }
    }
  }

  private String normalizeLegacyUploadUrl(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return rawValue;
    }
    if (!rawValue.contains(LEGACY_LOCAL_UPLOAD_PREFIX)) {
      return rawValue;
    }
    String normalizedBase = normalizeUploadBaseUrl(uploadBaseUrl);
    return rawValue.replace(LEGACY_LOCAL_UPLOAD_PREFIX, normalizedBase);
  }

  private String normalizeUploadBaseUrl(String rawBaseUrl) {
    if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
      return LEGACY_LOCAL_UPLOAD_PREFIX;
    }
    String normalized = rawBaseUrl.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

}
