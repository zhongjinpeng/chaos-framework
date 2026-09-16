package com.michael.chaos.autoconfigure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.mq.MessagePublisher;
import com.michael.chaos.mq.kafka.KafkaMessagePublisher;
import com.michael.chaos.mq.reliable.OutboxMessageRepository;
import com.michael.chaos.mq.reliable.OutboxPublisher;
import com.michael.chaos.mq.reliable.ReliableMessageDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * MQ 自动装配测试。
 */
class ChaosMqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosMqAutoConfiguration.class, ChaosMqOutboxAutoConfiguration.class))
            .withPropertyValues("chaos.mq.outbox.dispatcher.initial-delay=1h");

    /**
     * 存在 KafkaTemplate 和 JdbcOperations 时应装配完整 outbox 链路，且写入端不顶替真实发布器。
     */
    @Test
    void shouldWireOutboxChainWithRealPublisher() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(JdbcOperations.class, () -> mock(JdbcTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class)).isInstanceOf(KafkaMessagePublisher.class);
                    assertThat(context).hasSingleBean(OutboxMessageRepository.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).hasSingleBean(ReliableMessageDispatcher.class);
                    assertThat(context).hasSingleBean(ChaosMqOutboxAutoConfiguration.OutboxDispatchLifecycle.class);
                    assertThat(context.getBean(ChaosMqOutboxAutoConfiguration.OutboxDispatchLifecycle.class).isRunning())
                            .isTrue();
                });
    }

    /**
     * 没有真实发布器时只注册 outbox 写入端，不启动派发器。
     */
    @Test
    void shouldNotRegisterDispatcherWithoutPublisher() {
        contextRunner
                .withBean(JdbcOperations.class, () -> mock(JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(ReliableMessageDispatcher.class);
                });
    }

    /**
     * 派发器可以通过配置关闭。
     */
    @Test
    void shouldAllowDisablingDispatcher() {
        contextRunner
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(JdbcOperations.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("chaos.mq.outbox.dispatcher.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(ReliableMessageDispatcher.class);
                });
    }
}
