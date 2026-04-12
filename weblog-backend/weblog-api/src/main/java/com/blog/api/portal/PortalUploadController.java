package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.api.security.UploadGuardService;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.security.UploadValidationService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.common.util.IpUtil;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.ContentModerationService;
import com.blog.infra.oss.StorageFacade;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * 用户端文件上传接口（用于广告申请素材上传）
 */
@Tag(name = "用户端-文件上传", description = "广告申请图片上传")
@RestController
@RequestMapping("/api/portal/upload")
public class PortalUploadController {

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
    private static final String DEFAULT_USAGE_TYPE = "ad_apply";
    private static final String UPLOAD_RATE_LIMIT_KEY = "upload_rate_limit";

    private void checkUploadEnabled() {
        if (!storageFacade.isStorageEnabled()) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
    }

    @Operation(summary = "上传广告申请图片")
    @PostMapping("/image")
    @RateLimit(key = "portal-upload-image", capacity = 300, seconds = 300)
    public Result<String> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "ad_apply") String usageType,
            HttpServletRequest request)
            throws IOException {
        checkUploadEnabled();
        StpUtil.checkLogin();
        dynamicRateLimitPolicyService.enforcePerIp(
                "portal-upload-image",
                UPLOAD_RATE_LIMIT_KEY,
                20,
                1,
                300,
                300,
                IpUtil.getClientIp(request),
                "上传过于频繁，请稍后再试"
        );
        uploadValidationService.validateImage(file);

        String normalizedUsageType = normalizeUsageType(usageType);
        Long userId = StpUtil.getLoginIdAsLong();
        uploadGuardService.consumeUpload(
                UploadGuardService.UploadScene.PORTAL_IMAGE,
                userId,
                IpUtil.getClientIp(request),
                file.getSize());

        String url;
        String objectKey;
        url = storageFacade.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
        objectKey = storageFacade.extractObjectKey(url);
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.FAIL, "上传成功但无法解析文件路径");
        }

        String ext = file.getOriginalFilename() != null
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1)
                : "";

        if (ossResourceService != null) {
            ossResourceService.record(
                    file.getOriginalFilename(),
                    objectKey,
                    file.getSize(),
                    ext,
                    file.getContentType(),
                    url,
                    userId,
                    normalizedUsageType);
        }

        if (contentModerationService != null) {
            contentModerationService.checkImage(url, null);
        }

        return Result.success(url);
    }

    private String normalizeUsageType(String usageType) {
        String normalized = usageType == null ? DEFAULT_USAGE_TYPE : usageType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return DEFAULT_USAGE_TYPE;
        }
        normalized = normalized.replaceAll("[^a-z0-9_-]", "");
        if (normalized.isBlank()) {
            return DEFAULT_USAGE_TYPE;
        }
        return switch (normalized) {
            case "avatar", "content", "other", "ad_apply" -> normalized;
            default -> DEFAULT_USAGE_TYPE;
        };
    }
}
