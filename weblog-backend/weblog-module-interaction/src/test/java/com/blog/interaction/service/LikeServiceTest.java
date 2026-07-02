package com.blog.interaction.service;

import com.blog.interaction.mapper.UserLikeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceTest {

  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private LikeService likeService;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = createValueOperationsMock();
    SetOperations<String, String> setOperations = createSetOperationsMock();
    UserLikeMapper userLikeMapper = mock(UserLikeMapper.class);
    jdbcTemplate = mock(JdbcTemplate.class);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    likeService = new LikeService(redisTemplate, userLikeMapper, jdbcTemplate);
  }

  @SuppressWarnings("unchecked")
  private ValueOperations<String, String> createValueOperationsMock() {
    return (ValueOperations<String, String>) mock(ValueOperations.class);
  }

  @SuppressWarnings("unchecked")
  private SetOperations<String, String> createSetOperationsMock() {
    return (SetOperations<String, String>) mock(SetOperations.class);
  }

  @Test
  void shouldReturnAllCountsFromRedisWhenFullyHit() {
    List<Long> commentIds = List.of(1L, 2L);
    when(valueOperations.multiGet(List.of("comment:like:1", "comment:like:2")))
      .thenReturn(List.of("3", "4"));

    Map<Long, Long> result = likeService.getCommentLikeCounts(commentIds);

    assertEquals(3L, result.get(1L));
    assertEquals(4L, result.get(2L));
    verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    verify(valueOperations, never()).multiSet(any());
  }

  @Test
  void shouldBackfillRedisWhenPartiallyMissed() {
    List<Long> commentIds = List.of(1L, 2L);
    when(valueOperations.multiGet(List.of("comment:like:1", "comment:like:2")))
      .thenReturn(Arrays.asList("3", null));
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
      .thenReturn(List.of(Map.of("id", 2L, "like_count", 6L)));

    Map<Long, Long> result = likeService.getCommentLikeCounts(commentIds);

    assertEquals(3L, result.get(1L));
    assertEquals(6L, result.get(2L));
    verify(valueOperations).multiSet(Map.of("comment:like:2", "6"));
  }

  @Test
  void shouldUseZeroWhenDbHasNoRowsForMissingIds() {
    List<Long> commentIds = List.of(9L);
    when(valueOperations.multiGet(List.of("comment:like:9"))).thenReturn(Collections.singletonList(null));
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
      .thenReturn(List.of());

    Map<Long, Long> result = likeService.getCommentLikeCounts(commentIds);

    assertEquals(0L, result.get(9L));
    verify(valueOperations).multiSet(Map.of("comment:like:9", "0"));
  }

  @Test
  void shouldFallbackToDbWhenRedisValueMalformed() {
    List<Long> commentIds = List.of(7L);
    when(valueOperations.multiGet(List.of("comment:like:7"))).thenReturn(List.of("not-number"));
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
      .thenReturn(List.of(Map.of("id", 7L, "like_count", 12L)));

    Map<Long, Long> result = likeService.getCommentLikeCounts(commentIds);

    assertEquals(12L, result.get(7L));
    verify(valueOperations).multiSet(Map.of("comment:like:7", "12"));
  }
}
