package com.blog.api.portal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.blog.common.result.Result;
import com.blog.common.util.IpUtil;
import com.blog.common.util.PageParamUtil;
import com.blog.content.dto.PostVO;
import com.blog.content.entity.Category;
import com.blog.content.service.CategoryService;
import com.blog.content.service.PostService;
import com.blog.content.service.TagService;
import com.blog.interaction.service.ReadCountService;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端文章接口
 */
@Tag(name = "用户端-文章", description = "文章列表、详情、上下篇导航")
@RestController
@RequestMapping("/api/portal/post")
@RequiredArgsConstructor
public class PortalPostController {

    private final PostService postService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final ReadCountService readCountService;

    @Operation(summary = "文章列表（分页）")
    @GetMapping
    @RateLimit(key = "portal-post-list", capacity = 120, seconds = 60)
    public Result<IPage<PostVO>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "标签ID") @RequestParam(required = false) Long tagId,
            @Parameter(description = "分类Slug") @RequestParam(required = false) String categorySlug,
            @Parameter(description = "标签Slug") @RequestParam(required = false) String tagSlug,
            @Parameter(description = "排序方式：latest-最新 / hottest-最热") @RequestParam(required = false) String sortBy) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        // slug 优先级高于 id
        if (categorySlug != null && !categorySlug.isBlank() && categoryId == null) {
            Category cat = categoryService.getBySlug(categorySlug);
            categoryId = cat.getId();
        }
        if (tagSlug != null && !tagSlug.isBlank() && tagId == null) {
            com.blog.content.entity.Tag tag = tagService.getBySlug(tagSlug);
            tagId = tag.getId();
        }
        return Result.success(postService.portalPage(pageParams.pageNum(), pageParams.pageSize(), categoryId, tagId, sortBy));
    }

    @Operation(summary = "文章详情（含上下篇导航、阅读计数）")
    @GetMapping("/{slug}")
    @RateLimit(key = "portal-post-detail", capacity = 180, seconds = 60)
    public Result<Map<String, Object>> detail(@PathVariable String slug, HttpServletRequest request) {
        PostVO post = postService.getBySlug(slug);

        // 互动计数使用 Redis 实时值，避免详情缓存导致计数滞后
        postService.refreshInteractionCounts(post);

        // 记录阅读（设备指纹防刷）
        String ua = request.getHeader("User-Agent");
        String ip = IpUtil.getClientIp(request);
        readCountService.recordView(post.getId(), ua, ip, null);

        // 加上 Redis 实时增量
        long redisIncrement = readCountService.getViewCount(post.getId());
        post.setViewCount(post.getViewCount() + (int) redisIncrement);

        PostVO prev = postService.getPrevPost(post.getId());
        PostVO next = postService.getNextPost(post.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("post", post);
        data.put("prev", prev);
        data.put("next", next);
        return Result.success(data);
    }

    @Operation(summary = "今日发布文章列表")
    @GetMapping("/today")
    @RateLimit(key = "portal-post-today", capacity = 60, seconds = 60)
    public Result<List<PostVO>> listTodayPosts(
            @Parameter(description = "最大返回条数，默认8，最大20")
            @RequestParam(defaultValue = "8") int limit) {
        limit = Math.max(1, Math.min(limit, 20));
        return Result.success(postService.listTodayPosts(limit));
    }

    @Operation(summary = "最近发布文章列表")
    @GetMapping("/recent")
    @RateLimit(key = "portal-post-recent", capacity = 60, seconds = 60)
    public Result<List<PostVO>> listRecentPosts(
            @Parameter(description = "最大返回条数，默认10，最大20")
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.max(1, Math.min(limit, 20));
        return Result.success(postService.listRecentPosts(limit));
    }

    @Operation(summary = "获取相似文章")
    @GetMapping("/{id}/similar")
    @RateLimit(key = "portal-post-similar", capacity = 90, seconds = 60)
    public Result<List<PostVO>> getSimilarPosts(@PathVariable Long id) {
        return Result.success(postService.getSimilarPosts(id, 6));
    }


}
