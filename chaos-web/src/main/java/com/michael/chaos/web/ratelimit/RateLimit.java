package com.michael.chaos.web.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Web 接口限流注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 当前接口每秒允许的请求数；小于等于 0 时使用全局默认值。
     */
    int permitsPerSecond() default -1;
}
