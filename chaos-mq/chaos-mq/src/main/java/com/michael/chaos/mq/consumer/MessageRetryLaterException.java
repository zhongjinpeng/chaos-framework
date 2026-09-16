package com.michael.chaos.mq.consumer;

/**
 * 消息暂时不能消费、需要 MQ 稍后重投的异常。
 *
 * <p>典型场景是同一消息正被另一个消费者处理。此时不能直接返回（会被 ack，如果另一个消费者随后失败，消息就丢了），
 * 应抛出该异常让 Kafka/RocketMQ listener 走重试。</p>
 */
public class MessageRetryLaterException extends RuntimeException {

    /**
     * 创建稍后重试异常。
     */
    public MessageRetryLaterException(String message) {
        super(message);
    }
}
