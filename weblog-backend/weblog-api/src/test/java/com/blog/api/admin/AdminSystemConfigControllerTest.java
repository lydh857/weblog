package com.blog.api.admin;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.api.scheduler.RankingComputeScheduler;
import com.blog.api.scheduler.SecurityLogCleanupScheduler;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import com.blog.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSystemConfigControllerTest {

  @Mock
  private SystemConfigService systemConfigService;

  @Mock
  private RankingComputeScheduler rankingComputeScheduler;

  @Mock
  private SecurityLogCleanupScheduler securityLogCleanupScheduler;

  @Mock
  private HttpServletRequest request;

  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  @InjectMocks
  private AdminSystemConfigController adminSystemConfigController;

  @Test
  void shouldRejectInvalidAutoBlockKeyPrefixes() {
    Map<String, String> configs = Map.of(
      "rate_limit_auto_block_key_prefixes", "sendCode,invalid-key"
    );

    BusinessException ex = assertThrows(BusinessException.class,
      () -> adminSystemConfigController.batchUpdate(configs, request));

    assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getCode());
    verify(systemConfigService, never()).batchUpdate(configs);
  }

  @Test
  void shouldRejectDynamicRateLimitStepTooLarge() {
    Map<String, String> configs = Map.of("send_code_rate_limit", "60");
    when(systemConfigService.getValue("send_code_rate_limit")).thenReturn("3");

    BusinessException ex = assertThrows(BusinessException.class,
      () -> adminSystemConfigController.batchUpdate(configs, request));

    assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getCode());
    verify(systemConfigService, never()).batchUpdate(configs);
  }
}
