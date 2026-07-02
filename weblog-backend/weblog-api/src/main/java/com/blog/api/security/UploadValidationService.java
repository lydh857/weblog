package com.blog.api.security;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 上传校验服务（基于系统配置）
 */
@Service
@RequiredArgsConstructor
public class UploadValidationService {

    private static final String UPLOAD_MAX_SIZE_MB_KEY = "upload_max_size_mb";
    private static final String UPLOAD_ALLOWED_TYPES_KEY = "upload_allowed_types";

    private static final int DEFAULT_MAX_SIZE_MB = 5;
    private static final int MIN_MAX_SIZE_MB = 1;
    private static final int MAX_MAX_SIZE_MB = 50;
    private static final Set<String> DEFAULT_ALLOWED_TYPES = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final SystemConfigService systemConfigService;

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先选择图片");
        }

        long maxBytes = resolveMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片大小不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持图片文件上传");
        }

        String ext = extractExt(file.getOriginalFilename());
        Set<String> allowedTypes = resolveAllowedTypes();
        if (!allowedTypes.contains(ext)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持该图片类型，仅允许: " + String.join(",", allowedTypes));
        }
    }

    public void validateImageExtension(String ext) {
        String normalized = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        Set<String> allowedTypes = resolveAllowedTypes();
        if (!allowedTypes.contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "不支持该图片类型，仅允许: " + String.join(",", allowedTypes));
        }
    }

    private long resolveMaxBytes() {
        int configured = systemConfigService.getIntValue(UPLOAD_MAX_SIZE_MB_KEY, DEFAULT_MAX_SIZE_MB);
        int safe = (configured < MIN_MAX_SIZE_MB || configured > MAX_MAX_SIZE_MB) ? DEFAULT_MAX_SIZE_MB : configured;
        return safe * 1024L * 1024L;
    }

    private Set<String> resolveAllowedTypes() {
        String raw = systemConfigService.getValue(UPLOAD_ALLOWED_TYPES_KEY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ALLOWED_TYPES;
        }
        Set<String> parsed = Arrays.stream(raw.split(","))
                .map(item -> item == null ? "" : item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return parsed.isEmpty() ? DEFAULT_ALLOWED_TYPES : parsed;
    }

    private String extractExt(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
