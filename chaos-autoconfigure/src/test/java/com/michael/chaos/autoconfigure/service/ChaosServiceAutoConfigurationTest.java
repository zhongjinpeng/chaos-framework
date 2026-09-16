package com.michael.chaos.autoconfigure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.service.context.TraceContextTaskDecorator;
import com.michael.chaos.service.retry.RetryExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Service 自动装配测试。
 */
class ChaosServiceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosServiceAutoConfiguration.class))
            .withPropertyValues("chaos.service.retry.backoff-ms=0");

    /**
     * 默认注册上下文传播 TaskDecorator。
     */
    @Test
    void shouldRegisterTraceContextTaskDecorator() {
        contextRunner.run(context -> assertThat(context.getBean(TaskDecorator.class))
                .isInstanceOf(TraceContextTaskDecorator.class));
    }

    /**
     * 用户自定义 TaskDecorator 时框架让位，保证容器中只有一个装饰器，Boot 的 getIfUnique 才能生效。
     */
    @Test
    void shouldBackOffWhenCustomTaskDecoratorPresent() {
        contextRunner.withUserConfiguration(CustomDecoratorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context.getBean(TaskDecorator.class)).isNotInstanceOf(TraceContextTaskDecorator.class);
                });
    }

    /**
     * 默认重试执行器不重试业务异常，但会重试其他异常。
     */
    @Test
    void defaultRetryShouldSkipBizException() {
        contextRunner.run(context -> {
            RetryExecutor executor = context.getBean(RetryExecutor.class);
            AtomicInteger bizAttempts = new AtomicInteger();
            assertThatThrownBy(() -> executor.execute((Runnable) () -> {
                bizAttempts.incrementAndGet();
                throw new BizException(CommonErrorCode.BAD_REQUEST);
            })).isInstanceOf(BizException.class);
            assertThat(bizAttempts).hasValue(1);

            AtomicInteger ioAttempts = new AtomicInteger();
            assertThatThrownBy(() -> executor.execute((Runnable) () -> {
                ioAttempts.incrementAndGet();
                throw new IllegalStateException("temporary");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(ioAttempts).hasValue(3);
        });
    }

    /**
     * Micrometer 上下文桥接需要显式开启。
     */
    @Test
    void micrometerBridgeShouldBeOptIn() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean("chaosContextRegistryRegistrar"));
        contextRunner.withPropertyValues("chaos.service.context-propagation.micrometer-enabled=true")
                .run(context -> assertThat(context).hasBean("chaosContextRegistryRegistrar"));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoratorConfiguration {

        @Bean
        TaskDecorator customTaskDecorator() {
            return runnable -> runnable;
        }
    }
}
