package com.michael.chaos.mq;

/**
 * 不依赖 Kafka、RocketMQ 或其他中间件的消息发布端口。
 */
public interface MessagePublisher {

    /**
     * 发布框架消息信封。
     */
    void publish(MessageEnvelope<?> message);
}
