package com.michael.chaos.autoconfigure.mq;

import com.michael.chaos.mq.MessagePublisher;
import com.michael.chaos.mq.reliable.DeadLetterMessageHandler;
import com.michael.chaos.mq.reliable.FixedRetryBackoffStrategy;
import com.michael.chaos.mq.reliable.LoggingDeadLetterMessageHandler;
import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.mq.reliable.OutboxDispatchScheduler;
import com.michael.chaos.mq.reliable.OutboxHealth;
import com.michael.chaos.mq.reliable.actuate.OutboxHealthIndicator;
import com.michael.chaos.mq.reliable.OutboxMessageRepository;
import com.michael.chaos.mq.reliable.OutboxPublisher;
import com.michael.chaos.mq.reliable.ReliableMessageDispatcher;
import com.michael.chaos.mq.reliable.ReliableMessagePublisher;
import com.michael.chaos.mq.reliable.ReliableMessageStatus;
import com.michael.chaos.mq.reliable.RetryBackoffStrategy;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * outbox 写入端与派发器自动装配。
 *
 * <p>装配链路：{@link OutboxPublisher}（业务事务内写 outbox）→ {@link ReliableMessageDispatcher}
 * → {@link OutboxDispatchLifecycle} 定时派发与清理。派发器需要同时存在 outbox 仓储和真实 {@link MessagePublisher}；
 * 只有 outbox 仓储时仍可写入，消息会等到接入 MQ 后再发送。</p>
 */
@AutoConfiguration(after = ChaosMqAutoConfiguration.class)
@ConditionalOnProperty(prefix = "chaos.mq.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "com.michael.chaos.mq.reliable.ReliableMessageDispatcher")
public class ChaosMqOutboxAutoConfiguration {

    /**
     * 注册 outbox 写入端，业务事务内注入 {@link OutboxPublisher} 发布消息。
     */
    @Bean
    @ConditionalOnBean(OutboxMessageRepository.class)
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(OutboxMessageRepository repository) {
        return new ReliableMessagePublisher(repository);
    }

    /**
     * 默认死信处理器：输出 ERROR 日志，便于告警发现。
     */
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterMessageHandler deadLetterMessageHandler() {
        return new LoggingDeadLetterMessageHandler();
    }

    /**
     * 默认固定间隔重试策略。
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryBackoffStrategy retryBackoffStrategy(ChaosMqProperties properties) {
        return new FixedRetryBackoffStrategy(properties.getOutbox().getDispatcher().getRetryBackoff());
    }

    /**
     * 同时存在 outbox 仓储和真实发布器时注册派发器。
     */
    @Bean
    @ConditionalOnBean({OutboxMessageRepository.class, MessagePublisher.class})
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.mq.outbox.dispatcher", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ReliableMessageDispatcher reliableMessageDispatcher(
            OutboxMessageRepository repository,
            MessagePublisher messagePublisher,
            DeadLetterMessageHandler deadLetterMessageHandler,
            RetryBackoffStrategy retryBackoffStrategy,
            ChaosMqProperties properties,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        ChaosMqProperties.Outbox outbox = properties.getOutbox();
        return new ReliableMessageDispatcher(
                repository,
                messagePublisher,
                deadLetterMessageHandler,
                retryBackoffStrategy,
                Clock.systemUTC(),
                outbox.getDispatcher().getMaxRetryTimes(),
                outbox.getClaimTimeout(),
                metricsProvider.getIfAvailable());
    }

    /**
     * 注册 outbox 积压判定。
     */
    @Bean
    @ConditionalOnBean(OutboxMessageRepository.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.mq.outbox.health", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public OutboxHealth outboxHealth(OutboxMessageRepository repository, ChaosMqProperties properties) {
        ChaosMqProperties.Health health = properties.getOutbox().getHealth();
        return new OutboxHealth(repository, health.getPendingThreshold(), health.getDeadLetterThreshold());
    }

    /**
     * 存在 Actuator 时把积压判定暴露为健康项。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class OutboxHealthIndicatorConfiguration {

        @Bean
        @ConditionalOnBean(OutboxHealth.class)
        @ConditionalOnMissingBean
        OutboxHealthIndicator chaosMqOutboxHealthIndicator(OutboxHealth outboxHealth) {
            return new OutboxHealthIndicator(outboxHealth);
        }
    }

    /**
     * 存在 Micrometer 时把积压深度注册为 gauge。
     *
     * <p>健康检查只回答"是否超阈值"，趋势和容量规划需要连续曲线，因此两者都要有。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class OutboxMetricsConfiguration {

        @Bean
        @ConditionalOnBean({OutboxMessageRepository.class, MeterRegistry.class})
        @ConditionalOnMissingBean(name = "chaosMqOutboxPendingGauge")
        Gauge chaosMqOutboxPendingGauge(OutboxMessageRepository repository, MeterRegistry meterRegistry) {
            // countByStatus 不支持时返回 -1，Prometheus 上表现为负值，一眼能看出"没接上"而不是"积压为 0"。
            return Gauge.builder(ChaosMeterNames.MQ_OUTBOX_PENDING,
                            repository, r -> r.countByStatus(ReliableMessageStatus.PENDING))
                    .description("Outbox messages waiting to be dispatched")
                    .register(meterRegistry);
        }
    }

    /**
     * 注册派发调度器，随 Spring 容器启动和关闭。
     */
    @Bean
    @ConditionalOnBean({OutboxMessageRepository.class, MessagePublisher.class})
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.mq.outbox.dispatcher", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public OutboxDispatchLifecycle outboxDispatchLifecycle(
            ReliableMessageDispatcher dispatcher,
            ChaosMqProperties properties) {
        ChaosMqProperties.Dispatcher dispatcherProperties = properties.getOutbox().getDispatcher();
        ChaosMqProperties.Cleanup cleanup = properties.getOutbox().getCleanup();
        return new OutboxDispatchLifecycle(new OutboxDispatchScheduler(dispatcher, new OutboxDispatchScheduler.Settings(
                dispatcherProperties.getInitialDelay(),
                dispatcherProperties.getInterval(),
                dispatcherProperties.getBatchSize(),
                10,
                cleanup.isEnabled(),
                cleanup.getInterval(),
                cleanup.getRetention(),
                cleanup.getBatchSize())));
    }

    /**
     * 把 outbox 调度器接入 Spring 生命周期。
     */
    public static class OutboxDispatchLifecycle implements SmartLifecycle {

        private final OutboxDispatchScheduler scheduler;

        /**
         * 创建生命周期适配器。
         */
        public OutboxDispatchLifecycle(OutboxDispatchScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void start() {
            scheduler.start();
        }

        @Override
        public void stop() {
            scheduler.stop();
        }

        @Override
        public boolean isRunning() {
            return scheduler.isRunning();
        }

        /**
         * 返回底层调度器。
         */
        public OutboxDispatchScheduler scheduler() {
            return scheduler;
        }
    }
}
