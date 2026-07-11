package com.blog.api.support;

import com.blog.api.portal.support.PageParamNormalizer;
import com.blog.common.constant.CommonConstant;
import com.blog.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageParamUtilSecurityTest {

    @Test
    void shouldClampPageSizeToMaxLimit() {
        PageParamNormalizer.PageParams pageParams = PageParamNormalizer.normalize(1, 100000, (int) CommonConstant.MAX_PAGE_SIZE);

        assertEquals(1, pageParams.pageNum());
        assertEquals((int) CommonConstant.MAX_PAGE_SIZE, pageParams.pageSize());
    }

    @Test
    void shouldRejectInvalidPageNum() {
        assertThrows(BusinessException.class, () -> PageParamNormalizer.normalize(0, 10, (int) CommonConstant.MAX_PAGE_SIZE));
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(BusinessException.class, () -> PageParamNormalizer.normalize(1, 0, (int) CommonConstant.MAX_PAGE_SIZE));
    }
}
