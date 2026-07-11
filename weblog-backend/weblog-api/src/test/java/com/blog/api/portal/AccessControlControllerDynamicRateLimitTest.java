package com.blog.api.portal;

import com.blog.api.portal.support.DeviceFingerprintService;
import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.mapper.PostMapper;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.interaction.service.AccessControlService;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessControlControllerDynamicRateLimitTest {

  @Mock
  private AccessControlService accessControlService;
  @Mock
  private CaptchaService captchaService;
  @Mock
  private DeviceFingerprintService deviceFingerprintService;
  @Mock
  private PostMapper postMapper;
  @Mock
  private SystemConfigService systemConfigService;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  @InjectMocks
  private AccessControlController accessControlController;

  @Test
  void shouldRejectRecordReadWhenDynamicRateLimitExceeded() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "阅读记录请求过于频繁，请稍后再试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> accessControlController.recordRead(1L, request, new MockHttpServletResponse()));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(accessControlService, never()).recordRead(anyString(), any());
  }

  @Test
  void shouldRejectUnlockWhenDynamicRateLimitExceeded() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "解锁请求过于频繁，请稍后再试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> accessControlController.unlock("captcha-token", request, new MockHttpServletResponse()));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(captchaService, never()).validateVerifyTokenOrThrow(anyString(), anyString(), anyString());
  }
}
