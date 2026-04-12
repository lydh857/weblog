package com.blog.api.admin;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.security.UploadGuardService;
import com.blog.api.security.UploadValidationService;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.ContentModerationService;
import com.blog.infra.oss.StorageFacade;
import com.blog.infra.security.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
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

    @Autowired
    private StorageFacade storageFacade;

    @Autowired(required = false)
    private OssResourceService ossResourceService;

    @Autowired(required = false)
    private ContentModerationService contentModerationService;

    @Autowired
    private UploadGuardService uploadGuardService;

    @Autowired
    private UploadValidationService uploadValidationService;

    @Autowired
    private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

    private static final String UPLOAD_RATE_LIMIT_KEY = "upload_rate_limit";

    private void checkUploadEnabled() {
        if (!storageFacade.isStorageEnabled()) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
    }

    @Operation(summary = "上传图片")
    @PostMapping("/image")
    @AuditLog(module = "文件管理", operation = "CREATE", description = "上传图片")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false, defaultValue = "other") String usageType,
                                      HttpServletRequest request)
            throws IOException {
        checkUploadEnabled();
        StpUtil.checkLogin();
        String clientIp = IpUtil.getClientIp(request);
        dynamicRateLimitPolicyService.enforcePerIp(
                "admin-upload-image",
                UPLOAD_RATE_LIMIT_KEY,
                20,
                1,
                300,
                300,
                clientIp,
                "上传过于频繁，请稍后再试"
        );
        uploadValidationService.validateImage(file);
        Long userId = StpUtil.getLoginIdAsLong();
        uploadGuardService.consumeUpload(
                UploadGuardService.UploadScene.ADMIN_IMAGE,
                userId,
                clientIp,
                file.getSize());

        boolean isTemp = "content".equals(usageType);
        String url;
        String objectKey;

        url = isTemp
                ? storageFacade.uploadTemp(file.getInputStream(), file.getOriginalFilename(), file.getSize())
                : storageFacade.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
        objectKey = storageFacade.extractObjectKey(url);
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.FAIL, "上传成功但无法解析文件路径");
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

    @Operation(summary = "获取直传签名（前端直传对象存储）")
    @GetMapping("/sign")
    public Result<Map<String, String>> getUploadSign(@RequestParam String ext,
                                                     @RequestParam(required = false, defaultValue = "other") String usageType,
                                                     HttpServletRequest request) {
        if (!storageFacade.supportsDirectUpload()) {
            throw new BusinessException(ResultCode.FAIL, "当前存储后端不支持直传签名");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        uploadGuardService.consumeDirectSign(userId, IpUtil.getClientIp(request));
        String normalizedExt = normalizeExt(ext);
        String normalizedUsageType = normalizeUsageType(usageType);
        Map<String, String> policy = storageFacade.generateUploadPolicy(normalizedExt);

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
        if (!storageFacade.supportsDirectUpload()) {
            throw new BusinessException(ResultCode.FAIL, "当前存储后端不支持直传校验");
        }
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        String objectKey = request.objectKey().trim();

        try {
            StorageFacade.VerifiedUploadResult verified = storageFacade.verifyUploadedObject(objectKey);
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
            storageFacade.delete(objectKey);
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
