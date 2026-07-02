package com.blog.api.crawler.util;

import cn.hutool.core.util.StrUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;

public final class CrawlerIdempotencyKeyUtil {

    private CrawlerIdempotencyKeyUtil() {
    }

    public static String buildCandidateKey(String deviceId, String sourceFingerprint) {
        String safeDeviceId = normalize(deviceId, "deviceId");
        String safeFingerprint = normalize(sourceFingerprint, "sourceFingerprint");
        return "candidate:" + safeDeviceId + ":" + safeFingerprint;
    }

    public static String buildPushKey(String deviceId, Long candidateId, int version) {
        String safeDeviceId = normalize(deviceId, "deviceId");
        if (candidateId == null || candidateId <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "candidateId 非法");
        }
        if (version <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "version 非法");
        }
        return "push:" + safeDeviceId + ":" + candidateId + ":v" + version;
    }

    private static String normalize(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, fieldName + " 不能为空");
        }
        return value.trim().toLowerCase().replaceAll("[^a-z0-9:_-]", "-");
    }
}
