package com.blog.api.portal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.result.Result;
import com.blog.content.entity.Category;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.content.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户端分类接口（导航用）
 */
@Tag(name = "用户端-分类", description = "分类树、文章计数")
@RestController
@RequestMapping("/api/portal/category")
@RequiredArgsConstructor
public class PortalCategoryController {

    private final CategoryService categoryService;
    private final PostMapper postMapper;

    @Operation(summary = "根据slug获取分类信息")
    @GetMapping("/slug/{slug}")
    public Result<CategoryTreeVO> getBySlug(@PathVariable String slug) {
        Category cat = categoryService.getBySlug(slug);
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.id = cat.getId();
        vo.name = cat.getName();
        vo.slug = cat.getSlug();
        vo.description = cat.getDescription();
        vo.postCount = 0;
        vo.children = List.of();
        return Result.success(vo);
    }

    @Operation(summary = "分类树（含文章数）")
    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> tree() {
        List<Category> all = categoryService.listAll();

        // 统计每个分类的已发布文章数
        LambdaQueryWrapper<Post> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Post::getStatus, "published")
                .select(Post::getCategoryId, Post::getSubCategoryId);
        List<Post> posts = postMapper.selectList(countWrapper);

        Map<Long, Long> categoryCount = posts.stream()
                .filter(p -> p != null && p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Post::getCategoryId, Collectors.counting()));
        Map<Long, Long> subCategoryCount = posts.stream()
                .filter(p -> p != null && p.getSubCategoryId() != null)
                .collect(Collectors.groupingBy(Post::getSubCategoryId, Collectors.counting()));

        // 合并计数
        subCategoryCount.forEach((k, v) -> categoryCount.merge(k, v, Long::sum));

        // 构建树（parentId 为 null 时视为顶级分类，归入 0L）
        Map<Long, List<Category>> grouped = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));

        List<CategoryTreeVO> tree = buildTree(0L, grouped, categoryCount);
        return Result.success(tree);
    }

    private List<CategoryTreeVO> buildTree(Long parentId, Map<Long, List<Category>> grouped,
                                           Map<Long, Long> countMap) {
        List<Category> children = grouped.getOrDefault(parentId, List.of());
        List<CategoryTreeVO> result = new ArrayList<>();
        for (Category cat : children) {
            CategoryTreeVO vo = new CategoryTreeVO();
            vo.id = cat.getId();
            vo.name = cat.getName();
            vo.slug = cat.getSlug();
            vo.description = cat.getDescription();
            vo.postCount = countMap.getOrDefault(cat.getId(), 0L).intValue();
            vo.children = buildTree(cat.getId(), grouped, countMap);
            // 累加子分类文章数
            int childTotal = vo.children.stream().mapToInt(c -> c.postCount).sum();
            vo.postCount = vo.postCount + childTotal;
            result.add(vo);
        }
        return result;
    }

    public static class CategoryTreeVO {
        private Long id;
        private String name;
        private String slug;
        private String description;
        private Integer postCount;
        private List<CategoryTreeVO> children;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getPostCount() { return postCount; }
        public void setPostCount(Integer postCount) { this.postCount = postCount; }
        public List<CategoryTreeVO> getChildren() { return children; }
        public void setChildren(List<CategoryTreeVO> children) { this.children = children; }
    }
}
