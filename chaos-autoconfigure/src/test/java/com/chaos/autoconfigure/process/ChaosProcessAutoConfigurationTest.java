package com.chaos.autoconfigure.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chaos.core.metrics.ChaosMeterNames;
import com.chaos.core.metrics.ChaosMetrics;
import com.chaos.process.ProcessEngine;
import com.chaos.process.jdbc.JdbcProcessEngine;
import com.chaos.process.redis.RedisProcessEngine;
import com.chaos.process.statemachine.StateMachineDefinition;
import com.chaos.process.statemachine.StateMachineEngine;
import com.chaos.process.support.InMemoryProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 流程引擎自动装配测试。
 */
class ChaosProcessAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosProcessAutoConfiguration.class));

    @Test
    void shouldRegisterInMemoryEngineForDevelopmentByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosProcessProperties.class);
            assertThat(context).hasSingleBean(ProcessEngine.class);
            assertThat(context).hasSingleBean(InMemoryProcessEngine.class);
            assertThat(context).hasSingleBean(StateMachineEngine.class);
        });
    }

    @Test
    void shouldBackOffForCustomProcessEngine() {
        contextRunner.withUserConfiguration(CustomEngineConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProcessEngine.class);
                    assertThat(context).doesNotHaveBean(InMemoryProcessEngine.class);
                });
    }

    @Test
    void shouldAllowDisablingAutoConfigurationOrDefaultEngine() {
        contextRunner.withPropertyValues("chaos.process.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ProcessEngine.class));
        contextRunner.withPropertyValues("chaos.process.engine=none")
                .run(context -> assertThat(context).doesNotHaveBean(ProcessEngine.class));
    }

    @Test
    void shouldSelectJdbcEngineWhenConfiguredAndJdbcIsAvailable() {
        contextRunner.withPropertyValues("chaos.process.engine=jdbc")
                .withBean(JdbcOperations.class, () -> mock(JdbcOperations.class))
                .run(context -> assertThat(context).hasSingleBean(JdbcProcessEngine.class)
                        .doesNotHaveBean(InMemoryProcessEngine.class));
    }

    @Test
    void shouldSelectRedisEngineWhenConfiguredAndRedisIsAvailable() {
        contextRunner.withPropertyValues("chaos.process.engine=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasSingleBean(RedisProcessEngine.class)
                        .doesNotHaveBean(InMemoryProcessEngine.class));
    }

    @Test
    void shouldNotCreatePersistentEngineWithoutItsInfrastructureBean() {
        contextRunner.withPropertyValues("chaos.process.engine=jdbc")
                .run(context -> assertThat(context).doesNotHaveBean(ProcessEngine.class));
        contextRunner.withPropertyValues("chaos.process.engine=redis")
                .run(context -> assertThat(context).doesNotHaveBean(ProcessEngine.class));
    }

    @Test
    void shouldRejectInMemoryEngineInProduction() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("InMemoryProcessEngine"));
    }

    @Test
    void shouldConnectStateMachineEngineToGovernanceMetrics() {
        ChaosMetrics metrics = mock(ChaosMetrics.class);
        contextRunner.withBean(ChaosMetrics.class, () -> metrics)
                .run(context -> {
                    StateMachineDefinition<String, String, Void> definition =
                            StateMachineDefinition.<String, String, Void>builder()
                                    .transition("DRAFT", "SUBMIT", "PENDING")
                                    .build();

                    context.getBean(StateMachineEngine.class).fire(definition, "DRAFT", "SUBMIT", null);

                    verify(metrics).increment(
                            ChaosMeterNames.PROCESS_STATE_TRANSITIONS,
                            ChaosMeterNames.TAG_OUTCOME,
                            "completed");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEngineConfiguration {

        @Bean
        ProcessEngine processEngine() {
            return mock(ProcessEngine.class);
        }
    }
}
