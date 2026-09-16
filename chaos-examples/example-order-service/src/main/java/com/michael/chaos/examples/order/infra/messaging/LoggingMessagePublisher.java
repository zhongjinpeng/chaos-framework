package com.michael.chaos.examples.order.infra.messaging;

import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例用消息发布器：只打印日志，代替 Kafka/RocketMQ。
 *
 * <p>示例服务不依赖真实 MQ，注册该发布器后 outbox 派发器会启动，可以观察消息从 PENDING 变为 SENT 的完整链路。
 * 真实服务引入 spring-kafka 或 rocketmq-spring 后，由 chaos-mq 自动装配真实发布器，不要保留该类。</p>
 */
public class LoggingMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessagePublisher.class);

    @Override
    public void publish(MessageEnvelope<?> message) {
        log.info("[example] publish message, topic={}, messageId={}", message.topic(), message.messageId());
    }
}
