package com.blog.common.util;

import com.blog.common.constant.CommonConstant;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;

/**
 * 分页参数标准化工具
 */
public final class PageParamUtil {

    private PageParamUtil() {
    }

    public static PageParams normalize(int pageNum, int pageSize) {
        return normalize(pageNum, pageSize, (int) CommonConstant.MAX_PAGE_SIZE);
    }

    public static PageParams normalize(int pageNum, int pageSize, int maxPageSize) {
        if (pageNum < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "pageNum 不能小于 1");
        }
        if (pageSize < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "pageSize 不能小于 1");
        }

        int safeMaxPageSize = Math.max(maxPageSize, 1);
        int normalizedPageSize = Math.min(pageSize, safeMaxPageSize);
        return new PageParams(pageNum, normalizedPageSize);
    }

    public record PageParams(int pageNum, int pageSize) {
    }
}
