package com.blog.interaction.service;

import com.blog.common.exception.BusinessException;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.mapper.CommentMapper;
import com.blog.infra.security.sensitive.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

  private CommentMapper commentMapper;
  private StringRedisTemplate redisTemplate;
  private SetOperations<String, String> setOperations;
  private CommentService commentService;

  @BeforeEach
  void setUp() {
    commentMapper = mock(CommentMapper.class);
    redisTemplate = mock(StringRedisTemplate.class);
    setOperations = createSetOperationsMock();
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
    AiReviewService aiReviewService = mock(AiReviewService.class);

    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(redisTemplate.execute(any(), any(), any())).thenReturn(0L);

    commentService = new CommentService(commentMapper, redisTemplate, jdbcTemplate, sensitiveWordService, aiReviewService);
  }

  @SuppressWarnings("unchecked")
  private SetOperations<String, String> createSetOperationsMock() {
    return (SetOperations<String, String>) mock(SetOperations.class);
  }

  @Test
  void shouldDeleteDistinctCommentIdsInBatch() {
    Long userId = 1L;
    List<Long> commentIds = List.of(10L, 10L, 11L);

    Comment first = new Comment();
    first.setId(10L);
    first.setUserId(userId);
    first.setPostId(101L);

    Comment second = new Comment();
    second.setId(11L);
    second.setUserId(userId);
    second.setPostId(101L);

    when(commentMapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(first, second));

    commentService.batchDeleteComments(userId, commentIds);

    verify(commentMapper).selectByIds(List.of(10L, 11L));
    verify(commentMapper).deleteByIds(List.of(10L, 11L));
    verify(setOperations, times(1)).add(eq("post:comment:dirty"), eq("101"));
  }

  @Test
  void shouldThrowNotFoundWhenCommentMissing() {
    Long userId = 1L;
    when(commentMapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> commentService.batchDeleteComments(userId, List.of(10L, 11L)));

    assertEquals("评论不存在", ex.getMessage());
    verify(commentMapper, never()).deleteByIds(any());
    verify(setOperations, never()).add(any(), any());
  }

  @Test
  void shouldThrowForbiddenWhenDeletingOthersComment() {
    Long userId = 1L;
    Comment other = new Comment();
    other.setId(10L);
    other.setUserId(2L);
    other.setPostId(101L);

    when(commentMapper.selectByIds(List.of(10L))).thenReturn(List.of(other));

    BusinessException ex = assertThrows(BusinessException.class,
      () -> commentService.batchDeleteComments(userId, List.of(10L)));

    assertEquals("只能删除自己的评论", ex.getMessage());
    verify(commentMapper, never()).deleteByIds(any());
    verify(setOperations, never()).add(any(), any());
  }

  @Test
  void shouldNotTouchRedisWhenBatchDeleteFails() {
    Long userId = 1L;
    Comment first = new Comment();
    first.setId(10L);
    first.setUserId(userId);
    first.setPostId(101L);

    Comment second = new Comment();
    second.setId(11L);
    second.setUserId(userId);
    second.setPostId(102L);

    when(commentMapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(first, second));
    doThrow(new RuntimeException("db delete error"))
      .when(commentMapper)
      .deleteByIds(List.of(10L, 11L));

    assertThrows(RuntimeException.class,
      () -> commentService.batchDeleteComments(userId, List.of(10L, 11L)));

    verify(setOperations, never()).add(eq("post:comment:dirty"), any());
    verify(redisTemplate, never()).execute(any(), any(), any());
  }
}
