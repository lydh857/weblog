package com.blog.content.service;

import com.blog.content.dto.PostAutoSaveRequest;
import com.blog.content.entity.Post;
import com.blog.content.entity.PostContent;
import com.blog.content.mapper.CategoryMapper;
import com.blog.content.mapper.PostContentMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.mapper.PostTagMapper;
import com.blog.content.mapper.TagMapper;
import com.blog.infra.oss.CdnService;
import com.blog.infra.oss.StorageFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceUpdateTimeTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostContentMapper postContentMapper;

    @Mock
    private PostTagMapper postTagMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private PostIndexService postIndexService;

    @Mock
    private TagService tagService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Mock
    private org.springframework.cache.CacheManager cacheManager;

    @Mock
    private CdnService cdnService;

    @Mock
    private StorageFacade storageFacade;

    @InjectMocks
    private PostService postService;

    @Test
    void shouldRefreshUpdateTimeWhenAutoSavingDraft() {
        LocalDateTime oldUpdateTime = LocalDateTime.now().minusDays(1);
        Post post = new Post();
        post.setId(1L);
        post.setAuthorId(99L);
        post.setTitle("旧标题");
        post.setUpdateTime(oldUpdateTime);
        when(postMapper.selectById(1L)).thenReturn(post);

        PostContent content = new PostContent();
        content.setId(1L);
        content.setContent("旧正文");
        when(postContentMapper.selectById(1L)).thenReturn(content);

        PostAutoSaveRequest request = new PostAutoSaveRequest();
        request.setTitle("新标题");
        request.setContent("新正文");

        postService.autoSave(1L, request, 99L);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).updateById(postCaptor.capture());
        Post savedPost = postCaptor.getValue();

        assertNotNull(savedPost.getUpdateTime());
        assertTrue(savedPost.getUpdateTime().isAfter(oldUpdateTime));
        assertEquals("新标题", savedPost.getTitle());
        verify(postContentMapper).updateById(any(PostContent.class));
    }
}
