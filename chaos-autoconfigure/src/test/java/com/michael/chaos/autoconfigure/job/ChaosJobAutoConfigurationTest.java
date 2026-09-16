package com.michael.chaos.autoconfigure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.job.DistributedJobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * 定时任务自动装配测试。
 */
class ChaosJobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosJobAutoConfiguration.class));

    /**
     * 默认开启调度并注册任务执行器。
     */
    @Test
    void shouldRegisterRunnerAndEnableScheduling() {
        contextRunner.withBean(DistributedLock.class, () -> mock(DistributedLock.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedJobRunner.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    /**
     * 可以只关闭 Spring 调度，保留任务执行器。
     */
    @Test
    void shouldAllowDisablingScheduling() {
        contextRunner.withPropertyValues("chaos.job.scheduling-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedJobRunner.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    /**
     * 总开关关闭时不注册任何 Bean。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner.withPropertyValues("chaos.job.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DistributedJobRunner.class));
    }
}
