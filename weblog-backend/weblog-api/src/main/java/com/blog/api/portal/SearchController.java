package com.blog.api.portal;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.Result;
import com.blog.common.result.ResultCode;
import com.blog.api.portal.support.PageParamNormalizer;
import com.blog.content.dto.SearchResult;
import com.blog.content.service.PostSearchService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.blog.common.constant.CommonConstant.MAX_PAGE_SIZE;

/**
 * 用户端搜索接口
 */
@Tag(name = "用户端-搜索", description = "全文检索")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private static final int MAX_KEYWORD_LENGTH = 80;

    private final PostSearchService postSearchService;

    @Operation(summary = "全文搜索文章")
    @GetMapping
    @RateLimit(key = "search", capacity = 30, seconds = 60, perIp = true, message = "搜索请求过于频繁，请稍后重试")
    public Result<SearchResult> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageParamNormalizer.PageParams pageParams = PageParamNormalizer.normalize(pageNum, pageSize, MAX_PAGE_SIZE);
        pageNum = pageParams.pageNum();
        pageSize = pageParams.pageSize();

        if (normalizedKeyword.isEmpty()) {
            SearchResult emptyResult = new SearchResult();
            emptyResult.setTotal(0);
            emptyResult.setHits(java.util.List.of());
            return Result.success(emptyResult);
        }

        if (normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词长度不能超过" + MAX_KEYWORD_LENGTH + "个字符");
        }

        if (containsControlChars(normalizedKeyword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词包含非法控制字符");
        }

        return Result.success(postSearchService.search(normalizedKeyword, pageNum, pageSize));
    }

    private boolean containsControlChars(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if ((ch >= 0x00 && ch <= 0x1F) || ch == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
