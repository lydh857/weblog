package com.blog.api.portal;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.captcha.service.CaptchaService;
import com.blog.infra.redis.RedisService;
import com.blog.system.dto.SendCodeRequest;
import com.blog.system.service.AuthService;
import com.blog.system.service.EmailCodeService;
import com.blog.system.service.EmailDomainPolicyService;
import com.blog.system.service.LoginLogService;
import com.blog.system.service.LoginSecurityPolicyService;
import com.blog.system.service.RememberTokenService;
import com.blog.system.service.SecurityRiskControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerDynamicRateLimitTest {

  @Mock
  private AuthService authService;
  @Mock
  private EmailCodeService emailCodeService;
  @Mock
  private CaptchaService captchaService;
  @Mock
  private RedisService redisService;
  @Mock
  private LoginLogService loginLogService;
  @Mock
  private RememberTokenService rememberTokenService;
  @Mock
  private SecurityRiskControlService securityRiskControlService;
  @Mock
  private LoginSecurityPolicyService loginSecurityPolicyService;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;
  @Mock
  private EmailDomainPolicyService emailDomainPolicyService;

  @InjectMocks
  private AuthController authController;

  @Test
  void shouldRejectSendCodeWhenDynamicRateLimitExceeded() {
    SendCodeRequest req = new SendCodeRequest();
    req.setEmail("foo@example.com");
    req.setScene("login");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "验证码发送过于频繁，请稍后再试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class), anyString(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> authController.sendCode(req, "captcha-token", request));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(emailCodeService, never()).sendCode(anyString(), anyString());
  }

  @Test
  void shouldRejectForgotPasswordWhenDynamicRateLimitExceeded() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");

    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "重置密码请求过于频繁，请稍后再试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class), anyString(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> authController.forgotPassword(Map.of(
        "email", "foo@example.com",
        "code", "123456",
        "password", "P@ssword123"
      ), "captcha-token", request));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(authService, never()).forgotPassword(anyString(), anyString(), anyString());
  }
}
