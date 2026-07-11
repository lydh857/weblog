package com.blog.api.portal.support;

import com.blog.common.util.PageParamUtil;

/**
 * 分页参数标准化
 */
public final class PageParamNormalizer {

    private PageParamNormalizer() {
    }

    public static PageParams normalize(int pageNum, int pageSize, long maxPageSize) {
        int safeMaxPageSize = (int) Math.max(1, Math.min(maxPageSize, Integer.MAX_VALUE));
        PageParamUtil.PageParams normalized = PageParamUtil.normalize(pageNum, pageSize, safeMaxPageSize);
        return new PageParams(normalized.pageNum(), normalized.pageSize());
    }

    public record PageParams(int pageNum, int pageSize) {
    }
}
