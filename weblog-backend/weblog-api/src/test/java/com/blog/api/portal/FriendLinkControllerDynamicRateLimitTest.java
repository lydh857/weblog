package com.blog.api.portal;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.service.FriendLinkService;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendLinkControllerDynamicRateLimitTest {

  @Mock
  private FriendLinkService friendLinkService;
  @Mock
  private SystemConfigService systemConfigService;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  @InjectMocks
  private FriendLinkController friendLinkController;

  @Test
  void shouldRejectUpdateMyLinkWhenDynamicRateLimitExceeded() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    FriendLinkController.FriendLinkApplyRequest body = new FriendLinkController.FriendLinkApplyRequest();
    body.setName("demo");
    body.setUrl("https://example.com");
    body.setLogo("https://example.com/logo.png");
    body.setDescription("desc");

    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "友链更新过于频繁，请稍后再试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> friendLinkController.updateMyLink(body, request));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(friendLinkService, never()).updateMyLink(any(), anyString(), anyString(), anyString(), anyString());
  }
}
