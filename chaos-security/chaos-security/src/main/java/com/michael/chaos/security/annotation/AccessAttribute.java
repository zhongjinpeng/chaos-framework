package com.michael.chaos.security.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ABAC 资源属性声明，配合 {@link RequireAccess#attributes()} 使用。
 *
 * <p>属性值是 SpEL 表达式，可以引用方法参数（{@code #order}、{@code #p0}）、当前登录用户（{@code #user}）
 * 和目标 Bean（{@code #root}）。</p>
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessAttribute {

    /**
     * 属性名，对应 ABAC 条件中的 {@code resource.<name>}。
     */
    String name();

    /**
     * 取值用的 SpEL 表达式。
     */
    String value();
}
