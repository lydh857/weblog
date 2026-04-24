package com.blog.api.admin;

import com.blog.api.security.DynamicRateLimitPolicyService;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.content.dto.WritingReqVO;
import com.blog.content.service.AiMetaService;
import com.blog.content.service.AiWritingService;
import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.mapper.AiPromptTemplateMapper;
import com.blog.infra.ai.service.AiConfigService;
import com.blog.infra.ai.service.PromptTemplateService;
import com.blog.infra.ai.service.TokenMeterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAiControllerDynamicRateLimitTest {

  @Mock
  private AiWritingService aiWritingService;
  @Mock
  private AiMetaService aiMetaService;
  private TokenMeterService tokenMeterService;
  @Mock
  private PromptTemplateService promptTemplateService;
  @Mock
  private AiPromptTemplateMapper promptTemplateMapper;
  @Mock
  private AiProperties aiProperties;
  @Mock
  private AiConfigService aiConfigService;
  @Mock
  private DynamicRateLimitPolicyService dynamicRateLimitPolicyService;

  @InjectMocks
  private AdminAiController adminAiController;

  @Test
  void shouldRejectWritingContinueWhenDynamicRateLimitExceeded() {
    doThrow(new BusinessException(ResultCode.RATE_LIMIT, "AI 请求过于频繁，请稍后重试"))
      .when(dynamicRateLimitPolicyService)
      .enforcePerIp(anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), any(), anyString());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> adminAiController.writingContinue(new WritingReqVO()));

    assertEquals(ResultCode.RATE_LIMIT.getCode(), ex.getCode());
    verify(aiWritingService, never()).continueWriting(any());
  }
}
