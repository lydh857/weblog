package com.blog.infra.security.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 标注在 Controller 方法上，自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    String AUDIT_APPEND_DESCRIPTION_ATTR = "audit.log.appendDescription";

    /** 操作模块 */
    String module() default "";

    /** 操作描述 */
    String description() default "";

    /** 操作类型：LOGIN / LOGOUT / CREATE / UPDATE / DELETE */
    String operation() default "";
}
