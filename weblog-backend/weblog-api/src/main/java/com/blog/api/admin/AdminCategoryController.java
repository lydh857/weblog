package com.blog.api.admin;

import com.blog.common.result.Result;
import com.blog.content.entity.Category;
import com.blog.content.entity.Post;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.service.CategoryService;
import com.blog.infra.security.audit.AuditLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端分类接口
 */
@Tag(name = "管理端-分类管理", description = "分类CRUD")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;

    @Operation(summary = "获取所有分类（含文章数）")
    @GetMapping
    public Result<List<Map<String, Object>>> listAll(@RequestParam(required = false) String keyword) {
        List<Category> categories;
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // 先搜索匹配的分类
            LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(Category::getName, kw);
            wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId);
            categories = categoryMapper.selectList(wrapper);
            // 补全：如果搜到子分类，把其父分类也加进来
            Set<Long> existIds = categories.stream().map(Category::getId).collect(Collectors.toSet());
            Set<Long> missingParentIds = categories.stream()
                    .filter(c -> c.getParentId() != null && c.getParentId() != 0 && !existIds.contains(c.getParentId()))
                    .map(Category::getParentId)
                    .collect(Collectors.toSet());
            if (!missingParentIds.isEmpty()) {
                List<Category> parents = categoryMapper.selectByIds(missingParentIds);
                categories.addAll(parents);
                // 重新排序
                categories.sort(Comparator.comparingInt((Category c) -> c.getSortOrder() != null ? c.getSortOrder() : 0)
                        .thenComparingLong(Category::getId));
            }
        } else {
            LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId);
            categories = categoryMapper.selectList(wrapper);
        }

        // 统计每个分类的文章数（categoryId + subCategoryId）
        Map<Long, Long> countMap = new HashMap<>();
        if (!categories.isEmpty()) {
            List<Long> ids = categories.stream().map(Category::getId).toList();
            // 主分类文章数
            LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
            postWrapper.in(Post::getCategoryId, ids).select(Post::getCategoryId);
            List<Post> posts = postMapper.selectList(postWrapper);
            for (Post p : posts) {
                countMap.merge(p.getCategoryId(), 1L, Long::sum);
            }
            // 子分类文章数
            LambdaQueryWrapper<Post> subWrapper = new LambdaQueryWrapper<>();
            subWrapper.in(Post::getSubCategoryId, ids).select(Post::getSubCategoryId);
            List<Post> subPosts = postMapper.selectList(subWrapper);
            for (Post p : subPosts) {
                if (p.getSubCategoryId() != null) {
                    countMap.merge(p.getSubCategoryId(), 1L, Long::sum);
                }
            }
        }

        List<Map<String, Object>> result = categories.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("slug", c.getSlug());
            map.put("description", c.getDescription());
            map.put("parentId", c.getParentId());
            map.put("sortOrder", c.getSortOrder());
            map.put("createTime", c.getCreateTime());
            map.put("updateTime", c.getUpdateTime());
            map.put("articleCount", countMap.getOrDefault(c.getId(), 0L));
            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @Operation(summary = "获取顶级分类")
    @GetMapping("/top")
    public Result<List<Category>> listTopLevel() {
        return Result.success(categoryService.listTopLevel());
    }

    @Operation(summary = "获取子分类")
    @GetMapping("/children/{parentId}")
    public Result<List<Category>> listChildren(@PathVariable Long parentId) {
        return Result.success(categoryService.listByParentId(parentId));
    }

    @Operation(summary = "获取分类详情")
    @GetMapping("/{id}")
    public Result<Category> getById(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @Operation(summary = "创建分类")
    @PostMapping
    @AuditLog(module = "分类管理", operation = "CREATE", description = "创建分类")
    public Result<Category> create(@RequestBody CategoryRequest req) {
        return Result.success(categoryService.create(
                req.getName(), req.getSlug(), req.getDescription(), req.getParentId(), req.getSortOrder()));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    @AuditLog(module = "分类管理", operation = "UPDATE", description = "更新分类")
    public Result<Category> update(@PathVariable Long id, @RequestBody CategoryRequest req) {
        return Result.success(categoryService.update(
                id, req.getName(), req.getSlug(), req.getDescription(), req.getParentId(), req.getSortOrder()));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    @AuditLog(module = "分类管理", operation = "DELETE", description = "删除分类")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除分类")
    @DeleteMapping("/batch")
    @AuditLog(module = "分类管理", operation = "DELETE", description = "批量删除分类")
    public Result<Void> batchDelete(@RequestBody BatchDeleteRequest req) {
        for (Long id : req.getIds()) {
            categoryService.delete(id);
        }
        return Result.success();
    }

    @Data
    public static class CategoryRequest {
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 50, message = "分类名称最长50字")
        private String name;
        @Size(max = 80, message = "Slug最长80字")
        private String slug;
        @Size(max = 200, message = "描述最长200字")
        private String description;
        private Long parentId;
        private Integer sortOrder;
    }

    @Data
    public static class BatchDeleteRequest {
        @NotEmpty(message = "ID列表不能为空")
        private List<Long> ids;
    }
}
