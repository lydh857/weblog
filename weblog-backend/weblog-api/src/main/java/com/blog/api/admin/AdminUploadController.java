package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.ContentModerationService;
import com.blog.infra.oss.LocalFileService;
import com.blog.infra.oss.OssService;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端文件上传接口
 */
@Tag(name = "管理端-文件上传", description = "图片上传与管理")
@RestController
@RequestMapping("/api/admin/upload")
public class AdminUploadController {

    @Autowired(required = false)
    private OssService ossService;

    @Autowired(required = false)
    private LocalFileService localFileService;

    @Autowired(required = false)
    private OssResourceService ossResourceService;

    @Autowired(required = false)
    private ContentModerationService contentModerationService;

    private void checkUploadEnabled() {
        if (ossService == null && localFileService == null) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
    }

    @Operation(summary = "上传图片")
    @PostMapping("/image")
    @AuditLog(module = "文件管理", operation = "CREATE", description = "上传图片")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false, defaultValue = "other") String usageType)
            throws IOException {
        checkUploadEnabled();
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        boolean isTemp = "content".equals(usageType);
        String url;
        String objectKey;

        if (ossService != null) {
            // OSS 模式
            url = isTemp
                    ? ossService.uploadTemp(file.getInputStream(), file.getOriginalFilename(), file.getSize())
                    : ossService.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
            objectKey = isTemp
                    ? ossService.extractObjectKey(url)
                    : url.substring(url.indexOf("/images/") + 1);
        } else {
            // 本地存储模式
            url = isTemp
                    ? localFileService.uploadTemp(file.getInputStream(), file.getOriginalFilename(), file.getSize())
                    : localFileService.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
            objectKey = localFileService.extractObjectKey(url);
        }

        String ext = file.getOriginalFilename() != null
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1)
                : "";

        if (ossResourceService != null) {
            ossResourceService.record(
                    file.getOriginalFilename(), objectKey, file.getSize(),
                    ext, file.getContentType(), url, userId, usageType);
        }

        if (contentModerationService != null) {
            contentModerationService.checkImage(url, null);
        }

        return Result.success(url);
    }

    @Operation(summary = "获取直传签名（前端直传OSS）")
    @GetMapping("/sign")
    public Result<Map<String, String>> getUploadSign(@RequestParam String ext,
                                                     @RequestParam(required = false, defaultValue = "other") String usageType) {
        if (ossService == null) {
            throw new BusinessException(ResultCode.FAIL, "直传签名仅在 OSS 模式下可用");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String normalizedExt = normalizeExt(ext);
        String normalizedUsageType = normalizeUsageType(usageType);
        Map<String, String> policy = ossService.generateUploadPolicy(normalizedExt);

        if (ossResourceService != null) {
            ossResourceService.record(
                    null, policy.get("objectKey"), null,
                    normalizedExt, null, policy.get("cdnUrl"), userId, normalizedUsageType);
        }

        return Result.success(policy);
    }

    @Operation(summary = "校验直传文件并回填元信息")
    @PostMapping("/sign/verify")
    public Result<Map<String, Object>> verifySignedUpload(@Valid @RequestBody VerifyUploadRequest request) {
        if (ossService == null) {
            throw new BusinessException(ResultCode.FAIL, "直传校验仅在 OSS 模式下可用");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String objectKey = request.objectKey().trim();

        try {
            OssService.VerifiedUploadResult verified = ossService.verifyUploadedObject(objectKey);
            if (ossResourceService != null) {
                ossResourceService.updateUploadedMetadata(
                        verified.objectKey(),
                        userId,
                        verified.fileSize(),
                        verified.fileType(),
                        verified.mimeType(),
                        verified.url());
            }
            return Result.success(Map.of(
                    "objectKey", verified.objectKey(),
                    "url", verified.url(),
                    "fileSize", verified.fileSize(),
                    "mimeType", verified.mimeType() == null ? "" : verified.mimeType(),
                    "fileType", verified.fileType()
            ));
        } catch (BusinessException ex) {
            cleanupInvalidUpload(objectKey, userId);
            throw ex;
        }
    }

    private void cleanupInvalidUpload(String objectKey, Long userId) {
        try {
            if (ossService != null) {
                ossService.delete(objectKey);
            }
        } catch (Exception ignore) {
            // ignore
        }
        if (ossResourceService != null) {
            ossResourceService.markDeletedByFilePath(objectKey, userId);
        }
    }

    private String normalizeExt(String ext) {
        if (ext == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "ext 不能为空");
        }
        String normalized = ext.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replaceAll("[^a-z0-9]", "");
        if (normalized.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "ext 格式非法");
        }
        return normalized;
    }

    private String normalizeUsageType(String usageType) {
        if (usageType == null || usageType.isBlank()) {
            return "other";
        }
        String normalized = usageType.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (normalized.isBlank()) {
            return "other";
        }
        return switch (normalized) {
            case "post", "avatar", "ad", "other", "content", "cover", "carousel", "ad_apply" -> normalized;
            default -> "other";
        };
    }

    public record VerifyUploadRequest(@NotBlank(message = "objectKey 不能为空") String objectKey) {}
}
