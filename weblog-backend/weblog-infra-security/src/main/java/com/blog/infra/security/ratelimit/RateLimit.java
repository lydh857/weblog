package com.blog.infra.security.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 基于 Bucket4j 令牌桶算法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 key 前缀（默认使用方法名）
     */
    String key() default "";

    /**
     * 时间窗口内允许的最大请求数
     */
    int capacity() default 10;

    /**
     * 时间窗口（秒）
     */
    int seconds() default 60;

    /**
     * 是否按 IP 限流（true=每个IP独立计数，false=全局计数）
     */
    boolean perIp() default true;

    /**
     * 限流提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}
