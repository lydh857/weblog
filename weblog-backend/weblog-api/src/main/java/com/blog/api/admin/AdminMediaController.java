package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.MediaVO;
import com.blog.content.entity.OssResource;
import com.blog.content.service.MediaReferenceService;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.OssService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 管理端媒体资源管理接口
 */
@Tag(name = "管理端-媒体管理", description = "OSS 资源列表、删除")
@RestController
@RequestMapping("/api/admin/media")
public class AdminMediaController {

  @Autowired(required = false)
  private OssResourceService ossResourceService;

  @Autowired(required = false)
  private MediaReferenceService mediaReferenceService;

  @Autowired(required = false)
  private OssService ossService;

  private void checkOssEnabled() {
    if (ossResourceService == null) {
      throw new BusinessException(ResultCode.FAIL, "OSS 未启用");
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
    pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
    Long userId = StpUtil.getLoginIdAsLong();
    boolean isAdmin = StpUtil.hasRole("admin");
    Long uploaderId = isAdmin ? null : userId;

    // 获取引用 URL 集合和引用详情
    Set<String> referencedUrls = mediaReferenceService != null
        ? mediaReferenceService.getReferencedUrls()
        : Set.of();
    Map<String, List<MediaVO.ReferenceDetail>> detailMap = mediaReferenceService != null
        ? mediaReferenceService.getReferenceDetailsMap()
        : Map.of();

    IPage<OssResource> page;
    if (referenceStatus != null && !referenceStatus.isEmpty()) {
      // 按引用状态筛选：在数据库层过滤，避免内存加载
      page = ossResourceService.pageByReferenceStatus(
          pageNum, pageSize, uploaderId, usageType, referenceStatus, referencedUrls);
    } else {
      // 无引用状态筛选：正常分页查询
      page = ossResourceService.page(pageNum, pageSize, uploaderId, usageType);
    }

    IPage<MediaVO> voPage = page.convert(r -> toMediaVO(r, referencedUrls, detailMap));
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

  /**
   * OssResource 转 MediaVO，设置 referenced 字段和引用详情
   * avatar 类型资源 referenced 为 null（不参与引用检测）
   */
  private MediaVO toMediaVO(OssResource resource, Set<String> referencedUrls,
                            Map<String, List<MediaVO.ReferenceDetail>> detailMap) {
    MediaVO vo = new MediaVO();
    vo.setId(resource.getId());
    vo.setFileName(resource.getFileName());
    vo.setFilePath(resource.getFilePath());
    vo.setFileSize(resource.getFileSize());
    vo.setFileType(resource.getFileType());
    vo.setMimeType(resource.getMimeType());
    vo.setUrl(resource.getUrl());
    vo.setUploaderId(resource.getUploaderId());
    vo.setUsageType(resource.getUsageType());
    vo.setCreateTime(resource.getCreateTime());

    // 生成缩略图 URL：OSS 模式使用图片处理参数，本地模式用原始 URL
    if (ossService != null && resource.getUrl() != null) {
      String objectKey = ossService.extractObjectKey(resource.getUrl());
      if (objectKey != null) {
        vo.setThumbnailUrl(ossService.getThumbnailUrl(objectKey));
      } else {
        vo.setThumbnailUrl(resource.getUrl());
      }
    } else {
      vo.setThumbnailUrl(resource.getUrl());
    }

    if (resource.getUsageType() != null && resource.getUsageType().startsWith("avatar")) {
      vo.setReferenced(null);
      vo.setReferenceDetails(List.of());
    } else {
      vo.setReferenced(resource.getUrl() != null && referencedUrls.contains(resource.getUrl()));
      vo.setReferenceDetails(detailMap.getOrDefault(resource.getUrl(), List.of()));
    }
    return vo;
  }

}
