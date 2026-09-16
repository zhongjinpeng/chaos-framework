package com.michael.chaos.autoconfigure.metrics;

import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.trace.monitor.MicrometerChaosMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 治理指标自动装配。
 *
 * <p>必须先于 web、gateway、tenant、security、mq 等自动装配执行，否则它们注入 {@link ChaosMetrics} 时
 * Bean 还没定义，会静默退化成不上报。</p>
 *
 * <p>没有 Micrometer 或没有 {@code MeterRegistry} 时注册 {@link NoopChaosMetrics}：
 * 让各组件无条件注入，调用点不必写判空分支——散落的判空既是噪音，也容易漏掉某个分支导致埋点缺失。</p>
 */
@AutoConfiguration(before = {
        com.michael.chaos.autoconfigure.web.ChaosWebAutoConfiguration.class,
        com.michael.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration.class,
        com.michael.chaos.autoconfigure.tenant.ChaosTenantAutoConfiguration.class,
        com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration.class,
        com.michael.chaos.autoconfigure.mq.ChaosMqOutboxAutoConfiguration.class
})
public class ChaosMetricsAutoConfiguration {

    /**
     * 存在 Micrometer 注册表时上报真实指标。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.micrometer.core.instrument.MeterRegistry",
            "com.michael.chaos.trace.monitor.MicrometerChaosMetrics"
    })
    static class MicrometerConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean(ChaosMetrics.class)
        ChaosMetrics chaosMetrics(MeterRegistry meterRegistry) {
            return new MicrometerChaosMetrics(meterRegistry);
        }
    }

    /**
     * 没有 Micrometer 时注册空实现。
     */
    @Bean
    @ConditionalOnMissingBean(ChaosMetrics.class)
    public ChaosMetrics noopChaosMetrics() {
        return NoopChaosMetrics.instance();
    }
}
