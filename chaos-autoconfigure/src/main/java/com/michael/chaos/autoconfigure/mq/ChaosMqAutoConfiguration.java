package com.michael.chaos.autoconfigure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.mq.MessagePublisher;
import com.michael.chaos.mq.jdbc.JdbcOutboxMessageRepository;
import com.michael.chaos.mq.jdbc.OutboxSchemaVerifier;
import com.michael.chaos.mq.kafka.KafkaMessagePublisher;
import com.michael.chaos.mq.reliable.OutboxMessageRepository;
import com.michael.chaos.mq.rocketmq.RocketMqMessagePublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * MQ adapter 自动装配。
 *
 * <p>本类负责基础设施 Bean：Kafka/RocketMQ {@link MessagePublisher}（真实 transport）和 JDBC
 * {@link OutboxMessageRepository}。outbox 写入端和派发器在 {@link ChaosMqOutboxAutoConfiguration} 中装配，
 * 拆成两个自动装配类是为了让 {@code @ConditionalOnBean} 能稳定看到这里注册的 Bean。</p>
 *
 * <p>可选中间件的 Bean 放在带 {@code @ConditionalOnClass} 的嵌套配置里：如果写在顶层方法上，
 * classpath 缺少 Kafka 或 RocketMQ 时，Spring 反射顶层配置类的方法签名会直接失败。</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
})
@EnableConfigurationProperties(ChaosMqProperties.class)
@ConditionalOnClass(name = "com.michael.chaos.mq.MessagePublisher")
public class ChaosMqAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaosMqAutoConfiguration.class);

    /**
     * Kafka 发布器装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({KafkaTemplate.class, KafkaMessagePublisher.class})
    static class KafkaPublisherConfiguration {

        /**
         * 存在 KafkaTemplate 时注册 Kafka 消息发布器。
         */
        @Bean
        @ConditionalOnBean(KafkaTemplate.class)
        @ConditionalOnMissingBean(MessagePublisher.class)
        public MessagePublisher kafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate, ChaosMqProperties properties) {
            return new KafkaMessagePublisher(kafkaTemplate, properties.getPublisher().getSendTimeout());
        }
    }

    /**
     * RocketMQ 发布器装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RocketMQTemplate.class, RocketMqMessagePublisher.class})
    static class RocketMqPublisherConfiguration {

        /**
         * 存在 RocketMQTemplate 时注册 RocketMQ 消息发布器。
         */
        @Bean
        @ConditionalOnBean(RocketMQTemplate.class)
        @ConditionalOnMissingBean(MessagePublisher.class)
        public MessagePublisher rocketMqMessagePublisher(RocketMQTemplate rocketMQTemplate, ChaosMqProperties properties) {
            return new RocketMqMessagePublisher(rocketMQTemplate, properties.getPublisher().getSendTimeout());
        }
    }

    /**
     * JDBC outbox 装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JdbcOperations.class, JdbcOutboxMessageRepository.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "chaos.mq.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class JdbcOutboxConfiguration {

        /**
         * 存在 JdbcOperations 时注册 JDBC outbox 仓储。
         */
        @Bean
        @ConditionalOnBean(JdbcOperations.class)
        @ConditionalOnMissingBean
        public OutboxMessageRepository outboxMessageRepository(
                JdbcOperations jdbcOperations,
                ObjectProvider<ObjectMapper> objectMapper,
                ChaosMqProperties properties) {
            return new JdbcOutboxMessageRepository(
                    jdbcOperations,
                    objectMapper.getIfAvailable(ObjectMapper::new),
                    properties.getOutbox().getTableName());
        }

        /**
         * 启动时检查 outbox 表，缺表时输出带建表脚本路径的 WARN（不阻断启动，数据库恢复后派发器会继续工作）。
         */
        @Bean
        @ConditionalOnBean(JdbcOperations.class)
        @ConditionalOnProperty(prefix = "chaos.diagnostics.outbox-schema-check", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        public SmartInitializingSingleton chaosOutboxSchemaVerifier(
                JdbcOperations jdbcOperations,
                ObjectProvider<OutboxMessageRepository> repository,
                ChaosMqProperties properties) {
            return () -> {
                // 只检查框架自带的 JDBC 仓储；业务自定义仓储的存储由业务自己负责。
                if (!(repository.getIfAvailable() instanceof JdbcOutboxMessageRepository)) {
                    return;
                }
                OutboxSchemaVerifier.verify(jdbcOperations, properties.getOutbox().getTableName())
                        .ifPresent(diagnostic -> LOGGER.warn("\n{}", diagnostic.format()));
            };
        }
    }
}
