package com.blog.api.portal.support;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 批量 ID 参数标准化：校验 + 去重
 */
public final class BatchIdNormalizer {

    private BatchIdNormalizer() {
    }

    public static List<Long> normalize(List<Long> ids, int maxCount, String maxCountMessage, String invalidIdMessage) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > maxCount) {
            throw new BusinessException(ResultCode.BAD_REQUEST, maxCountMessage);
        }

        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, invalidIdMessage);
            }
            unique.add(id);
        }
        return new ArrayList<>(unique);
    }
}
