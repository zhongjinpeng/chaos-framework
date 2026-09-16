package com.michael.chaos.autoconfigure.service;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.trace.monitor.ChaosObservationFilter;
import com.michael.chaos.service.context.ChaosContextThreadLocalAccessor;
import com.michael.chaos.service.context.TraceContextTaskDecorator;
import com.michael.chaos.service.event.DomainEventPublisher;
import com.michael.chaos.service.event.TransactionalDomainEventPublisher;
import com.michael.chaos.service.retry.RetryExecutor;
import com.michael.chaos.service.retry.SpringRetryExecutor;
import com.michael.chaos.service.transaction.SpringTransactionExecutor;
import com.michael.chaos.service.transaction.TransactionExecutor;
import io.micrometer.context.ContextRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service starter 自动装配。
 */
@AutoConfiguration(
        after = DataSourceTransactionManagerAutoConfiguration.class,
        beforeName = "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration"
)
@ConditionalOnClass(name = "com.michael.chaos.service.event.DomainEventPublisher")
@EnableConfigurationProperties(ChaosServiceProperties.class)
public class ChaosServiceAutoConfiguration {

    /**
     * 事务执行器（需要 spring-tx）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.transaction.PlatformTransactionManager")
    @EnableTransactionManagement
    static class TransactionConfiguration {

        /**
         * 注册事务执行器。
         */
        @Bean
        @ConditionalOnBean(PlatformTransactionManager.class)
        @ConditionalOnMissingBean
        TransactionExecutor transactionExecutor(PlatformTransactionManager transactionManager) {
            return new SpringTransactionExecutor(new TransactionTemplate(transactionManager));
        }
    }

    /**
     * 重试执行器（需要 spring-retry）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.retry.support.RetryTemplate")
    @EnableRetry
    static class RetryConfiguration {

        /**
         * 注册重试执行器。
         *
         * <p>默认不重试 {@link BizException}：业务规则拒绝（参数非法、状态不允许、幂等拒绝等）重试不会成功，
         * 只会放大下游压力并延迟错误返回。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        RetryExecutor retryExecutor(ChaosServiceProperties properties) {
            ChaosServiceProperties.Retry retry = properties.getRetry();
            int maxAttempts = Math.max(retry.getMaxAttempts(), 1);
            long backoffMs = Math.max(retry.getBackoffMs(), 0);
            RetryTemplateBuilder builder = RetryTemplate.builder()
                    .maxAttempts(maxAttempts)
                    .notRetryOn(List.of(BizException.class))
                    .traversingCauses();
            // Spring Retry 的 fixedBackoff 要求间隔 >= 1ms，0 表示不退避。
            RetryTemplate template = (backoffMs > 0 ? builder.fixedBackoff(backoffMs) : builder.noBackoff()).build();
            return new SpringRetryExecutor(template, maxAttempts, backoffMs);
        }
    }

    /**
     * 注册领域事件发布器。
     */
    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new TransactionalDomainEventPublisher(applicationEventPublisher);
    }

    /**
     * 注册异步任务上下文装饰器，自动传播 trace、租户、用户和 MDC。
     *
     * <p>按类型判断缺失：Boot 的 TaskExecutionAutoConfiguration 通过 {@code getIfUnique()} 获取装饰器，
     * 原实现按 Bean 名判断，用户再定义任意 TaskDecorator 时会同时存在两个，结果两个都不生效、上下文静默丢失。
     * 需要组合多个装饰器时，请自行声明
     * {@code new CompositeTaskDecorator(List.of(new TraceContextTaskDecorator(), yourDecorator))}。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator chaosTraceContextTaskDecorator() {
        return new TraceContextTaskDecorator();
    }

    /**
     * 注册框架通用指标标签。
     *
     * <p>只追加低基数的 {@code framework=chaos}。原实现给所有指标打上 {@code meter.namespace=http.server.requests}，
     * JVM、数据源等无关指标也带上 HTTP 语义，属于错误标签。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.micrometer.core.instrument.MeterRegistry",
            "org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer"
    })
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "chaosMeterRegistryCustomizer")
        MeterRegistryCustomizer<MeterRegistry> chaosMeterRegistryCustomizer() {
            return registry -> registry.config().commonTags("framework", "chaos");
        }
    }

    /**
     * 注册 Micrometer Observation 统一过滤器（需要 actuator 与 chaos-trace）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.micrometer.observation.ObservationRegistry",
            "org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer",
            "com.michael.chaos.trace.monitor.ChaosObservationFilter"
    })
    static class ObservationConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "chaosObservationRegistryCustomizer")
        ObservationRegistryCustomizer<ObservationRegistry> chaosObservationRegistryCustomizer() {
            return registry -> registry.observationConfig().observationFilter(new ChaosObservationFilter());
        }
    }

    /**
     * Micrometer Context Propagation 桥接（显式开启）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.context.ContextRegistry")
    @ConditionalOnProperty(prefix = "chaos.service.context-propagation", name = "micrometer-enabled", havingValue = "true")
    static class MicrometerContextPropagationConfiguration {

        /**
         * 把 chaos 上下文访问器注册到全局 ContextRegistry。
         */
        @Bean
        InitializingBean chaosContextRegistryRegistrar() {
            return () -> ContextRegistry.getInstance().registerThreadLocalAccessor(new ChaosContextThreadLocalAccessor());
        }
    }
}
