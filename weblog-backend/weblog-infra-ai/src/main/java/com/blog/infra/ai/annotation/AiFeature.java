package com.blog.infra.ai.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 功能标识注解
 * 标注在 Service 方法上，由 AiFeatureGuard 切面统一检查开关和限额
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiFeature {

  /**
   * 功能标识：writing / meta / chat
   */
  String value();
}
