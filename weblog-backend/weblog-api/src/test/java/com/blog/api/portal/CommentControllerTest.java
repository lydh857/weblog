package com.blog.api.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.service.AdminCommentService;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.redis.RedisService;
import com.blog.interaction.entity.Comment;
import com.blog.interaction.service.CommentService;
import com.blog.interaction.service.LikeService;
import com.blog.system.mapper.UserMapper;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

  @Mock
  private CommentService commentService;
  @Mock
  private LikeService likeService;
  @Mock
  private UserMapper userMapper;
  @Mock
  private PostMapper postMapper;
  @Mock
  private SystemConfigService systemConfigService;
  @Mock
  private RedisService redisService;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
  @Mock
  private AdminCommentService adminCommentService;
  @Mock
  private HttpServletRequest request;

  @Test
  void shouldRejectRepliesWhenParentIdInvalid() {
    CommentController controller = buildController();

    BusinessException ex = assertThrows(BusinessException.class,
      () -> controller.listReplies(0L, 1, 10));

    assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    assertEquals("评论ID不合法", ex.getMessage());
    verify(commentService, never()).getRepliesPage(anyLong(), anyInt(), anyInt());
  }

  @Test
  void shouldPassDeduplicatedCommentIdsWhenBatchDelete() {
    CommentController controller = buildController();

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);

      controller.batchDelete(List.of(10L, 10L, 11L), request);

      verify(commentService, times(1)).batchDeleteComments(9L, List.of(10L, 11L));
      verify(commentService, never()).batchDeleteComments(9L, List.of(10L, 10L, 11L));
    }
  }

  @Test
  void shouldRejectWhenBatchDeleteExceedsMaxCount() {
    CommentController controller = buildController();
    List<Long> oversized = LongStream.rangeClosed(1, 101).boxed().toList();

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);

      BusinessException ex = assertThrows(BusinessException.class,
        () -> controller.batchDelete(oversized, request));

      assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
      assertEquals("单次最多删除100条评论", ex.getMessage());
      verify(commentService, never()).batchDeleteComments(9L, oversized);
    }
  }

  @Test
  void shouldSkipBatchLikeCountQueryWhenRepliesPageEmpty() {
    CommentController controller = buildController();
    Page<Comment> emptyPage = new Page<>(1, 10);
    emptyPage.setRecords(List.of());
    emptyPage.setTotal(0);
    when(commentService.getRepliesPage(1L, 1, 10)).thenReturn(emptyPage);

    controller.listReplies(1L, 1, 10);

    verify(likeService, never()).getCommentLikeCounts(anyList());
  }

  @Test
  void shouldSkipBatchLikeCountQueryWhenPostCommentsAndRepliesEmpty() {
    CommentController controller = buildController();
    Page<Comment> emptyPage = new Page<>(1, 10);
    emptyPage.setRecords(List.of());
    emptyPage.setTotal(0);
    when(postMapper.existsById(1L)).thenReturn(1);
    when(commentService.getTopLevelComments(1L, 1, 10, "new")).thenReturn(emptyPage);

    controller.listByPost(1L, 1, 10, "new");

    verify(commentService, never()).getReplies(anyList());
    verify(likeService, never()).getCommentLikeCounts(anyList());
    verify(likeService, never()).getCommentLikedIds(eq(1L), anyList());
  }

  @Test
  void shouldApplyDynamicRateLimitWithExpectedParamsForBatchDelete() {
    CommentController controller = buildController();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
      stpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
      stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(9L);

      controller.batchDelete(List.of(10L), request);

      verify(dynamicRateLimitPolicyService, times(1)).enforcePerIp(
        eq("comment-batch-delete"),
        eq("comment_batch_delete_rate_limit"),
        eq(10),
        eq(1),
        eq(120),
        eq(60),
        eq("127.0.0.1"),
        eq("批量删除评论过于频繁，请稍后再试")
      );
    }
  }

  private CommentController buildController() {
    return new CommentController(
      commentService,
      likeService,
      userMapper,
      postMapper,
      systemConfigService,
      redisService,
      dynamicRateLimitPolicyService,
      adminCommentService
    );
  }
}
