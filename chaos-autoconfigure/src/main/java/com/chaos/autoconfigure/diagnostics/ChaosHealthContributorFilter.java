package com.chaos.autoconfigure.diagnostics;

import java.util.Map;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.health.ContributorRegistry;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.core.env.Environment;

/**
 * 从聚合健康注册表中移除默认关闭的低价值指标。
 *
 * <p>部分健康指标的自动配置条件早于普通自动装配执行，无法只靠 Chaos 提供的低优先级属性阻止 Bean 创建。
 * 因此在健康注册表初始化完成后按同一组配置移除指标；业务配置显式设为 {@code true} 时仍会保留。</p>
 */
final class ChaosHealthContributorFilter implements BeanPostProcessor {

    private static final Map<String, String> CONTRIBUTOR_PROPERTIES = Map.of(
            "discoveryComposite", "spring.cloud.discovery.client.composite-indicator.enabled",
            "ping", "management.health.ping.enabled",
            "reactiveDiscoveryClients", "spring.cloud.discovery.client.health-indicator.enabled",
            "refreshScope", "management.health.refresh.enabled",
            "ssl", "management.health.ssl.enabled");

    private final Environment environment;

    ChaosHealthContributorFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof HealthContributorRegistry registry) {
            removeDisabledContributors(registry);
        }
        if (bean instanceof ReactiveHealthContributorRegistry registry) {
            removeDisabledContributors(registry);
        }
        return bean;
    }

    private void removeDisabledContributors(ContributorRegistry<?> registry) {
        CONTRIBUTOR_PROPERTIES.forEach((contributor, property) -> {
            if (!environment.getProperty(property, Boolean.class, false)) {
                registry.unregisterContributor(contributor);
            }
        });
    }
}
