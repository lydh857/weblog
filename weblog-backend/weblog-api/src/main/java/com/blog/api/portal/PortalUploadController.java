package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.content.service.OssResourceService;
import com.blog.infra.oss.ContentModerationService;
import com.blog.infra.oss.LocalFileService;
import com.blog.infra.oss.OssService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private OssService ossService;

    @Autowired(required = false)
    private LocalFileService localFileService;

    @Autowired(required = false)
    private OssResourceService ossResourceService;

    @Autowired(required = false)
    private ContentModerationService contentModerationService;

    private static final long MAX_IMAGE_SIZE = 8L * 1024 * 1024;
    private static final String DEFAULT_USAGE_TYPE = "ad_apply";

    private void checkUploadEnabled() {
        if (ossService == null && localFileService == null) {
            throw new BusinessException(ResultCode.FAIL, "文件上传服务未启用");
        }
    }

    @Operation(summary = "上传广告申请图片")
    @PostMapping("/image")
    @RateLimit(key = "portal-upload-image", capacity = 20, seconds = 300)
    public Result<String> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "ad_apply") String usageType)
            throws IOException {
        checkUploadEnabled();
        StpUtil.checkLogin();

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先选择图片");
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片大小不能超过 8MB");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持图片文件上传");
        }

        String normalizedUsageType = normalizeUsageType(usageType);
        Long userId = StpUtil.getLoginIdAsLong();

        String url;
        String objectKey;
        if (ossService != null) {
            url = ossService.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
            objectKey = url.substring(url.indexOf("/images/") + 1);
        } else {
            url = localFileService.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
            objectKey = localFileService.extractObjectKey(url);
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
