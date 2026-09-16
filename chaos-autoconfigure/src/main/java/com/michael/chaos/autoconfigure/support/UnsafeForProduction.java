package com.michael.chaos.autoconfigure.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 Bean 定义方法注册的实现在生产环境中不安全。
 *
 * <p>配合 {@link ProductionSafetyEnforcer} 使用，当应用运行在生产模式时，
 * 如果容器中存在被标记类型的 Bean 实例，默认阻断启动；配置 {@code chaos.production-safety.fail-fast=false}
 * 时降级为告警。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UnsafeForProduction {

    /**
     * 被视为不安全的实现类。
     */
    Class<?>[] value();

    /**
     * 生产环境检测到该 Bean 时的告警提示。
     */
    String message();
}
