package com.blog.api.security;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.redis.RedisService;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicRateLimitPolicyServiceTest {

  @Mock
  private SystemConfigService systemConfigService;

  @Mock
  private RedisService redisService;

  @InjectMocks
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  @Test
  void shouldThrowWhenHitConfiguredThreshold() {
    when(systemConfigService.getIntValue("send_code_rate_limit", 3)).thenReturn(3);
    when(redisService.incrementWithExpire("rate:dynamic:sendCode:127.0.0.1", 60)).thenReturn(4L);

    BusinessException ex = assertThrows(BusinessException.class, () ->
      dynamicRateLimitPolicyService.enforcePerIp(
        "sendCode",
        "send_code_rate_limit",
        3,
        1,
        60,
        60,
        "127.0.0.1",
        "验证码发送过于频繁，请稍后再试"
      )
    );

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
  }

  @Test
  void shouldFallbackToDefaultCapacityWhenConfiguredOutOfRange() {
    when(systemConfigService.getIntValue("forgot_password_rate_limit", 5)).thenReturn(999);
    when(redisService.incrementWithExpire("rate:dynamic:forgotPassword:127.0.0.1", 60)).thenReturn(6L);

    BusinessException ex = assertThrows(BusinessException.class, () ->
      dynamicRateLimitPolicyService.enforcePerIp(
        "forgotPassword",
        "forgot_password_rate_limit",
        5,
        1,
        60,
        60,
        "127.0.0.1",
        "重置密码请求过于频繁，请稍后再试"
      )
    );

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
  }

  @Test
  void shouldAllowRequestAfterCounterResetWindow() {
    when(systemConfigService.getIntValue("send_code_rate_limit", 1)).thenReturn(1);
    when(redisService.incrementWithExpire("rate:dynamic:sendCode:127.0.0.1", 60)).thenReturn(1L, 1L);

    assertDoesNotThrow(() -> dynamicRateLimitPolicyService.enforcePerIp(
      "sendCode",
      "send_code_rate_limit",
      1,
      1,
      60,
      60,
      "127.0.0.1",
      "验证码发送过于频繁，请稍后再试"
    ));

    assertDoesNotThrow(() -> dynamicRateLimitPolicyService.enforcePerIp(
      "sendCode",
      "send_code_rate_limit",
      1,
      1,
      60,
      60,
      "127.0.0.1",
      "验证码发送过于频繁，请稍后再试"
    ));
  }
}
