package com.blog.api.portal;

import com.blog.common.result.Result;
import com.blog.content.mapper.PostTagMapper;
import com.blog.content.service.TagService;
import com.blog.content.vo.TagCloudVO;
import com.blog.infra.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端标签接口（标签云）
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "用户端-标签", description = "标签云、文章计数")
@RestController
@RequestMapping("/api/portal/tag")
@RequiredArgsConstructor
public class PortalTagController {

    private final TagService tagService;
    private final PostTagMapper postTagMapper;

    @Operation(summary = "根据slug获取标签信息")
    @GetMapping("/slug/{slug}")
    @RateLimit(key = "portal-tag-slug", capacity = 120, seconds = 60)
    public Result<TagCloudVO> getBySlug(@PathVariable String slug) {
        com.blog.content.entity.Tag tag = tagService.getBySlug(slug);
        TagCloudVO vo = new TagCloudVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setColor(tag.getColor());
        vo.setPostCount(0);
        return Result.success(vo);
    }

    @Operation(summary = "标签云（含文章数），支持按分类筛选")
    @GetMapping("/cloud")
    @RateLimit(key = "portal-tag-cloud", capacity = 120, seconds = 60)
    public Result<List<TagCloudVO>> cloud(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId) {
        List<TagCloudVO> result = postTagMapper.selectTagCloudWithCount(categoryId, subCategoryId);
        return Result.success(result);
    }
}
