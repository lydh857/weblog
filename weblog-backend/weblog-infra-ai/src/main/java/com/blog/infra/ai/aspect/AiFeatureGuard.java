package com.blog.infra.ai.aspect;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.infra.ai.annotation.AiFeature;
import com.blog.infra.ai.config.AiProperties;
import com.blog.infra.ai.service.AiConfigService;
import com.blog.infra.ai.service.TokenMeterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * AI 功能守卫切面
 * 在方法执行前检查：全局开关 → 功能开关 → 月度 token 限额
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AiFeatureGuard {

  private final AiProperties aiProperties;
  private final AiConfigService aiConfigService;
  private final TokenMeterService tokenMeterService;

  @Before("@annotation(com.blog.infra.ai.annotation.AiFeature)")
  public void checkFeature(JoinPoint jp) {
    // 1. 检查全局开关（连接参数，来自 AiProperties）
    if (!aiProperties.isEnabled()) {
      throw new BusinessException(ResultCode.AI_DISABLED);
    }

    // 2. 获取注解中的功能标识
    MethodSignature signature = (MethodSignature) jp.getSignature();
    AiFeature annotation = signature.getMethod().getAnnotation(AiFeature.class);
    String feature = annotation.value();

    // 3. 检查具体功能开关（运行时配置，来自 AiConfigService）
    if (!aiConfigService.isFeatureEnabled(feature)) {
      throw new BusinessException(ResultCode.AI_FEATURE_DISABLED);
    }

    // 4. 检查月度 token 限额
    if (tokenMeterService.isOverLimit()) {
      throw new BusinessException(ResultCode.AI_OVER_LIMIT);
    }
  }
}
