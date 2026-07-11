package com.blog.api.portal;

import com.blog.content.entity.Category;
import com.blog.content.entity.Post;
import com.blog.content.mapper.PostMapper;
import com.blog.content.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PortalCategoryControllerTest {

    @Mock
    private CategoryService categoryService;
    @Mock
    private PostMapper postMapper;

    @Test
    void shouldNotDoubleCountChildPostsInParentCategoryCount() {
        Category parent = category(1L, "后端", 0L);
        Category child = category(2L, "Java", 1L);
        List<Post> posts = List.of(
                post(1L, 2L),
                post(1L, 2L),
                post(1L, null)
        );
        Map<Long, Long> countMap = PortalCategoryController.buildCategoryPostCount(posts);
        Map<Long, List<Category>> grouped = List.of(parent, child).stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));

        PortalCategoryController controller = new PortalCategoryController(categoryService, postMapper);

        PortalCategoryController.CategoryTreeVO parentVo = controller.buildTree(0L, grouped, countMap).get(0);

        assertEquals(3, parentVo.getPostCount());
        assertEquals(2, parentVo.getChildren().get(0).getPostCount());
    }

    private static Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug("category-" + id);
        category.setParentId(parentId);
        return category;
    }

    private static Post post(Long categoryId, Long subCategoryId) {
        Post post = new Post();
        post.setCategoryId(categoryId);
        post.setSubCategoryId(subCategoryId);
        return post;
    }
}
