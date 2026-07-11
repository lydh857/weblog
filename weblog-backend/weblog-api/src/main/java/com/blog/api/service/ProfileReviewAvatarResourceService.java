package com.blog.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.content.entity.OssResource;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.content.service.OssResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileReviewAvatarResourceService {

    private static final String AVATAR_USAGE = "avatar";
    private static final String AVATAR_PENDING_FILE_PREFIX = "avatar_pending__";
    private static final String AVATAR_REJECTED_FILE_PREFIX = "avatar_rejected__";
    private static final String LEGACY_LOCAL_UPLOAD_PREFIX = "http://localhost:9091/uploads";

    private final OssResourceMapper ossResourceMapper;
    private final OssResourceService ossResourceService;

    @Value("${blog.upload.base-url:http://localhost:9091/uploads}")
    private String uploadBaseUrl;

    public void markApproved(String avatarUrl) {
        OssResource resource = findPendingAvatarResource(avatarUrl);
        if (resource == null) {
            return;
        }

        ossResourceMapper.update(null, new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getId, resource.getId())
                .set(OssResource::getFileName, toApprovedFileName(resource.getFileName())));
    }

    public void markRejected(String avatarUrl) {
        OssResource resource = findPendingAvatarResource(avatarUrl);
        if (resource == null) {
            return;
        }

        ossResourceMapper.update(null, new LambdaUpdateWrapper<OssResource>()
                .eq(OssResource::getId, resource.getId())
                .set(OssResource::getFileName, toRejectedFileName(resource.getFileName())));
    }

    public void deleteOldAvatar(Long userId, String oldAvatarUrl, String newAvatarUrl) {
        if (userId == null || oldAvatarUrl == null || oldAvatarUrl.isBlank()) {
            return;
        }
        if (isSameAvatarUrl(oldAvatarUrl, newAvatarUrl)) {
            return;
        }
        OssResource oldResource = findAvatarResource(userId, oldAvatarUrl);
        if (oldResource == null) {
            return;
        }
        try {
            ossResourceService.delete(oldResource.getId(), userId, true);
        } catch (Exception e) {
            log.warn("旧头像资源清理失败: userId={}, resourceId={}, error={}", userId, oldResource.getId(), e.getMessage());
        }
    }

    public String normalizeLegacyUploadUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || !rawValue.contains(LEGACY_LOCAL_UPLOAD_PREFIX)) {
            return rawValue;
        }
        String normalizedBase = uploadBaseUrl == null || uploadBaseUrl.isBlank() ? LEGACY_LOCAL_UPLOAD_PREFIX : uploadBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return rawValue.replace(LEGACY_LOCAL_UPLOAD_PREFIX, normalizedBase);
    }

    private OssResource findPendingAvatarResource(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        return ossResourceMapper.selectOne(new LambdaQueryWrapper<OssResource>()
                .eq(OssResource::getUrl, avatarUrl)
                .eq(OssResource::getUsageType, AVATAR_USAGE)
                .likeRight(OssResource::getFileName, AVATAR_PENDING_FILE_PREFIX)
                .orderByDesc(OssResource::getCreateTime)
                .last("LIMIT 1"));
    }

    private OssResource findAvatarResource(Long userId, String avatarUrl) {
        return ossResourceMapper.selectOne(new LambdaQueryWrapper<OssResource>()
                .eq(OssResource::getUrl, avatarUrl)
                .eq(OssResource::getUploaderId, userId)
                .eq(OssResource::getUsageType, AVATAR_USAGE)
                .orderByDesc(OssResource::getCreateTime)
                .last("LIMIT 1"));
    }

    private boolean isSameAvatarUrl(String oldAvatarUrl, String newAvatarUrl) {
        if (newAvatarUrl == null || newAvatarUrl.isBlank()) {
            return false;
        }
        return normalizeLegacyUploadUrl(oldAvatarUrl).equals(normalizeLegacyUploadUrl(newAvatarUrl));
    }

    private String toApprovedFileName(String fileName) {
        if (fileName == null) {
            return "avatar.webp";
        }
        if (fileName.startsWith(AVATAR_PENDING_FILE_PREFIX)) {
            return fileName.substring(AVATAR_PENDING_FILE_PREFIX.length());
        }
        if (fileName.startsWith(AVATAR_REJECTED_FILE_PREFIX)) {
            return fileName.substring(AVATAR_REJECTED_FILE_PREFIX.length());
        }
        return fileName;
    }

    private String toRejectedFileName(String fileName) {
        return AVATAR_REJECTED_FILE_PREFIX + toApprovedFileName(fileName);
    }
}
