package com.michael.chaos.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限范围标记。
 *
 * <p>基础设施拦截器可以根据该注解决定是否追加部门、用户、租户等数据范围条件。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 数据权限策略编码。
     */
    String value() default "default";
}
