package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.mapper.PostMapper;
import com.blog.interaction.mapper.UserLikeMapper;
import com.blog.interaction.service.FavoriteService;
import com.blog.interaction.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.LongStream;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class InteractionControllerTest {

  @Mock
  private LikeService likeService;
  @Mock
  private FavoriteService favoriteService;
  @Mock
  private PostMapper postMapper;
  @Mock
  private UserLikeMapper userLikeMapper;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
  @Mock
  private HttpServletRequest request;

  @Test
  void shouldRejectBatchUnfavoriteWhenInvalidIdPresent() {
    InteractionController controller = new InteractionController(likeService, favoriteService, postMapper, userLikeMapper, dynamicRateLimitPolicyService);

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);

      BusinessException ex = assertThrows(BusinessException.class,
        () -> controller.batchUnfavorite(List.of(0L, 0L), request));

      assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
      assertEquals("文章ID不合法", ex.getMessage());
      verify(postMapper, never()).selectExistingIds(anyList());
      verify(favoriteService, never()).batchUnfavorite(eq(9L), anyList());
    }
  }

  @Test
  void shouldRejectBatchUnfavoriteWhenPostNotFoundAfterDedup() {
    InteractionController controller = new InteractionController(likeService, favoriteService, postMapper, userLikeMapper, dynamicRateLimitPolicyService);

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
      when(postMapper.selectExistingIds(List.of(999L))).thenReturn(List.of());

      BusinessException ex = assertThrows(BusinessException.class,
        () -> controller.batchUnfavorite(List.of(999L, 999L), request));

      assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
      assertEquals("文章不存在", ex.getMessage());
      verify(postMapper, times(1)).selectExistingIds(List.of(999L));
      verify(favoriteService, never()).batchUnfavorite(eq(9L), anyList());
    }
  }

  @Test
  void shouldPassDeduplicatedIdsToBatchUnfavorite() {
    InteractionController controller = new InteractionController(likeService, favoriteService, postMapper, userLikeMapper, dynamicRateLimitPolicyService);

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
      when(postMapper.selectExistingIds(List.of(100L, 101L))).thenReturn(List.of(100L, 101L));

      controller.batchUnfavorite(List.of(100L, 100L, 101L), request);

      verify(postMapper, times(1)).selectExistingIds(List.of(100L, 101L));
      verify(favoriteService, times(1)).batchUnfavorite(9L, List.of(100L, 101L));
    }
  }

  @Test
  void shouldRejectWhenBatchUnfavoriteExceedsMaxCount() {
    InteractionController controller = new InteractionController(likeService, favoriteService, postMapper, userLikeMapper, dynamicRateLimitPolicyService);

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
      List<Long> oversized = LongStream.rangeClosed(1, 101).boxed().toList();

      BusinessException ex = assertThrows(BusinessException.class,
        () -> controller.batchUnfavorite(oversized, request));

      assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
      assertEquals("单次最多取消100条收藏", ex.getMessage());
      verify(postMapper, never()).selectExistingIds(anyList());
      verify(favoriteService, never()).batchUnfavorite(eq(9L), anyList());
    }
  }

  @Test
  void shouldApplyDynamicRateLimitWithExpectedParamsForBatchUnfavorite() {
    InteractionController controller = new InteractionController(likeService, favoriteService, postMapper, userLikeMapper, dynamicRateLimitPolicyService);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
      when(postMapper.selectExistingIds(List.of(100L))).thenReturn(List.of(100L));

      controller.batchUnfavorite(List.of(100L), request);

      verify(dynamicRateLimitPolicyService, times(1)).enforcePerIp(
        eq("interaction-favorite-batch"),
        eq("interaction_favorite_batch_rate_limit"),
        eq(20),
        eq(1),
        eq(120),
        eq(60),
        eq("127.0.0.1"),
        eq("批量取消收藏过于频繁，请稍后再试")
      );
    }
  }
}
