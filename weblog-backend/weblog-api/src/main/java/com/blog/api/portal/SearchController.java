package com.blog.api.portal;

import com.blog.common.result.Result;
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

    private final PostSearchService postSearchService;

    @Operation(summary = "全文搜索文章")
    @GetMapping
    @RateLimit(key = "search", capacity = 30, seconds = 60, perIp = true, message = "搜索请求过于频繁，请稍后重试")
    public Result<SearchResult> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        pageSize = (int) Math.min(pageSize, MAX_PAGE_SIZE);
        return Result.success(postSearchService.search(keyword, pageNum, pageSize));
    }
}
