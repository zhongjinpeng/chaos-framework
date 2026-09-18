package com.michael.chaos.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级统一授权标记，同时驱动 RBAC 与 ABAC 判定。
 *
 * <p>{@link Permission} 只能表达"要哪个权限编码"，无法表达"只能改自己的订单"这类与资源属性相关的规则；
 * 本注解把动作、资源和资源属性一起交给授权管理器，由 RBAC / ABAC 策略共同决策。</p>
 *
 * <pre>
 * &#64;RequireAccess(
 *         action = "order:update",
 *         resourceType = "order",
 *         resourceId = "#order.id",
 *         attributes = &#64;AccessAttribute(name = "ownerId", value = "#order.ownerId"))
 * public void update(OrderDTO order) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAccess {

    /**
     * 动作编码，同时作为 RBAC 的权限编码，不能为空。
     */
    String action();

    /**
     * 资源类型，对应 ABAC 条件中的 {@code resource.type}，为字面量。
     */
    String resourceType() default "";

    /**
     * 资源 ID 的 SpEL 表达式，对应 ABAC 条件中的 {@code resource.id}；字面量需要加引号。
     */
    String resourceId() default "";

    /**
     * 资源属性，对应 ABAC 条件中的 {@code resource.<name>}。
     */
    AccessAttribute[] attributes() default {};
}
