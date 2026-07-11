package com.blog.api.admin;

import com.blog.common.result.Result;
import com.blog.content.entity.PostTag;
import com.blog.content.entity.Tag;
import com.blog.content.mapper.PostTagMapper;
import com.blog.content.mapper.TagMapper;
import com.blog.content.service.TagService;
import com.blog.infra.security.audit.AuditLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端标签接口
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "管理端-标签管理", description = "标签CRUD")
@RestController
@RequestMapping("/api/admin/tag")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;
    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;

    @Operation(summary = "获取所有标签（含文章数）")
    @GetMapping
    public Result<List<Map<String, Object>>> listAll(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Tag::getName, keyword.trim());
        }
        wrapper.orderByDesc(Tag::getCreateTime);
        List<Tag> tags = tagMapper.selectList(wrapper);

        // 统计每个标签的文章数
        Map<Long, Long> countMap = new HashMap<>();
        if (!tags.isEmpty()) {
            List<Long> ids = tags.stream().map(Tag::getId).toList();
            LambdaQueryWrapper<PostTag> ptWrapper = new LambdaQueryWrapper<>();
            ptWrapper.in(PostTag::getTagId, ids).select(PostTag::getTagId);
            List<PostTag> postTags = postTagMapper.selectList(ptWrapper);
            for (PostTag pt : postTags) {
                countMap.merge(pt.getTagId(), 1L, Long::sum);
            }
        }

        List<Map<String, Object>> result = tags.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("name", t.getName());
            map.put("slug", t.getSlug());
            map.put("createTime", t.getCreateTime());
            map.put("updateTime", t.getUpdateTime());
            map.put("articleCount", countMap.getOrDefault(t.getId(), 0L));
            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @Operation(summary = "获取标签详情")
    @GetMapping("/{id}")
    public Result<Tag> getById(@PathVariable Long id) {
        return Result.success(tagService.getById(id));
    }

    @Operation(summary = "创建标签")
    @PostMapping
    @AuditLog(module = "标签管理", operation = "CREATE", description = "创建标签")
    public Result<Tag> create(@RequestBody TagRequest req) {
        return Result.success(tagService.create(req.getName(), req.getSlug()));
    }

    @Operation(summary = "批量创建标签")
    @PostMapping("/batch")
    @AuditLog(module = "标签管理", operation = "CREATE", description = "批量创建标签")
    public Result<List<Tag>> batchCreate(@RequestBody BatchCreateRequest req) {
        List<Tag> created = new ArrayList<>();
        for (BatchCreateRequest.TagItem item : req.getTags()) {
            try {
                created.add(tagService.create(item.getName(), item.getSlug()));
            } catch (Exception e) {
                // 跳过已存在的标签
            }
        }
        return Result.success(created);
    }

    @Operation(summary = "更新标签")
    @PutMapping("/{id}")
    @AuditLog(module = "标签管理", operation = "UPDATE", description = "更新标签")
    public Result<Tag> update(@PathVariable Long id, @RequestBody TagRequest req) {
        return Result.success(tagService.update(id, req.getName(), req.getSlug()));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    @AuditLog(module = "标签管理", operation = "DELETE", description = "删除标签")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除标签")
    @DeleteMapping("/batch")
    @AuditLog(module = "标签管理", operation = "DELETE", description = "批量删除标签")
    public Result<Void> batchDelete(@RequestBody BatchDeleteRequest req) {
        for (Long id : req.getIds()) {
            tagService.delete(id);
        }
        return Result.success();
    }

    @Data
    public static class TagRequest {
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 30, message = "标签名称最长30字")
        private String name;
        @Size(max = 80, message = "Slug最长80字")
        private String slug;
    }

    @Data
    public static class BatchCreateRequest {
        @NotEmpty(message = "标签列表不能为空")
        private List<TagItem> tags;

        @Data
        public static class TagItem {
            @NotBlank(message = "标签名称不能为空")
            private String name;
            private String slug;
        }
    }

    @Data
    public static class BatchDeleteRequest {
        @NotEmpty(message = "ID列表不能为空")
        private List<Long> ids;
    }
}
