package com.blog.interaction.service;

import com.blog.interaction.entity.UserFavorite;
import com.blog.interaction.mapper.UserFavoriteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FavoriteServiceTest {

  private StringRedisTemplate redisTemplate;
  private SetOperations<String, String> setOperations;
  private ValueOperations<String, String> valueOperations;
  private UserFavoriteMapper userFavoriteMapper;
  private FavoriteService favoriteService;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    setOperations = createSetOperationsMock();
    valueOperations = createValueOperationsMock();
    userFavoriteMapper = mock(UserFavoriteMapper.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.execute(any(), any(), any())).thenReturn(0L);
    when(valueOperations.get(any())).thenReturn("0");

    favoriteService = new FavoriteService(redisTemplate, userFavoriteMapper, jdbcTemplate);
  }

  @SuppressWarnings("unchecked")
  private SetOperations<String, String> createSetOperationsMock() {
    return (SetOperations<String, String>) mock(SetOperations.class);
  }

  @SuppressWarnings("unchecked")
  private ValueOperations<String, String> createValueOperationsMock() {
    return (ValueOperations<String, String>) mock(ValueOperations.class);
  }

  @Test
  void shouldBatchUnfavoriteDistinctActivePosts() {
    Long userId = 9L;
    List<Long> postIds = List.of(101L, 101L, 102L);

    UserFavorite first = new UserFavorite();
    first.setPostId(101L);
    UserFavorite second = new UserFavorite();
    second.setPostId(102L);
    when(userFavoriteMapper.selectList(any())).thenReturn(List.of(first, second));

    favoriteService.batchUnfavorite(userId, postIds);

    verify(setOperations).remove(eq("user:fav:9"), eq("101"), eq("102"));
    verify(setOperations, times(1)).add(eq("post:collect:dirty"), eq("101"));
    verify(setOperations, times(1)).add(eq("post:collect:dirty"), eq("102"));
    verify(userFavoriteMapper).softDeleteByUserAndPostIds(eq(userId), eq(List.of(101L, 102L)));
  }

  @Test
  void shouldReturnWhenNoActiveFavorites() {
    Long userId = 9L;
    when(userFavoriteMapper.selectList(any())).thenReturn(List.of());

    favoriteService.batchUnfavorite(userId, List.of(201L, 202L));

    verify(userFavoriteMapper, never()).softDeleteByUserAndPostIds(any(), any());
    verify(setOperations, never()).remove(any(), any());
  }

  @Test
  void shouldNotTouchRedisWhenBatchSoftDeleteFails() {
    Long userId = 9L;
    UserFavorite first = new UserFavorite();
    first.setPostId(101L);
    UserFavorite second = new UserFavorite();
    second.setPostId(102L);
    when(userFavoriteMapper.selectList(any())).thenReturn(List.of(first, second));
    doThrow(new RuntimeException("db error"))
      .when(userFavoriteMapper)
      .softDeleteByUserAndPostIds(eq(userId), eq(List.of(101L, 102L)));

    assertThrows(RuntimeException.class, () -> favoriteService.batchUnfavorite(userId, List.of(101L, 102L)));

    verify(setOperations, never()).remove(any(), any());
    verify(setOperations, never()).add(eq("post:collect:dirty"), any());
    verify(redisTemplate, never()).execute(any(), any(), any());
  }
}
