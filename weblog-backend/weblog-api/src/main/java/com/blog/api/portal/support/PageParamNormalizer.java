package com.blog.api.portal.support;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;

/**
 * 分页参数标准化
 */
public final class PageParamNormalizer {

    private PageParamNormalizer() {
    }

    public static PageParams normalize(int pageNum, int pageSize, long maxPageSize) {
        if (pageNum < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "pageNum 不能小于 1");
        }
        if (pageSize < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "pageSize 不能小于 1");
        }
        int normalizedPageSize = (int) Math.min(pageSize, maxPageSize);
        return new PageParams(pageNum, normalizedPageSize);
    }

    public record PageParams(int pageNum, int pageSize) {
    }
}
