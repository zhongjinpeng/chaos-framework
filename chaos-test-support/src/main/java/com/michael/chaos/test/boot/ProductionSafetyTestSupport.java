package com.michael.chaos.test.boot;

import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;

/**
 * 生产安全检查相关的自动装配测试辅助。
 *
 * <p>chaos 在生产模式下默认 fail-fast：发现 InMemory 限流/幂等、Noop 撤销服务等危险默认实现时阻止启动。
 * 自定义自动装配或替换默认实现的项目，应同时验证“生产模式能启动”和“危险实现会被拦截”两种情况：</p>
 *
 * <pre>{@code
 * ProductionSafetyTestSupport.productionMode(contextRunner)
 *         .run(context -> assertThat(context).hasFailed());
 * ProductionSafetyTestSupport.productionModeWarnOnly(contextRunner)
 *         .run(context -> assertThat(context).hasNotFailed());
 * }</pre>
 */
public final class ProductionSafetyTestSupport {

    /**
     * 显式开启生产模式的属性。
     */
    public static final String PRODUCTION_MODE = "chaos.production-safety.production-mode=true";

    /**
     * 关闭 fail-fast、只告警的属性。
     */
    public static final String WARN_ONLY = "chaos.production-safety.fail-fast=false";

    private ProductionSafetyTestSupport() {
    }

    /**
     * 以生产模式运行（默认 fail-fast）。
     */
    public static <T extends AbstractApplicationContextRunner<T, ?, ?>> T productionMode(T runner) {
        return runner.withPropertyValues(PRODUCTION_MODE);
    }

    /**
     * 以生产模式运行，但只告警不阻断启动。
     */
    public static <T extends AbstractApplicationContextRunner<T, ?, ?>> T productionModeWarnOnly(T runner) {
        return runner.withPropertyValues(PRODUCTION_MODE, WARN_ONLY);
    }
}
