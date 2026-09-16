package com.michael.chaos.mq.consumer;

import com.michael.chaos.mq.MessageEnvelope;

/**
 * 标准消息消费端口。
 *
 * @param <T> 消息体类型
 */
public interface MessageConsumer<T> {

    /**
     * 消费消息信封。
     */
    void consume(MessageEnvelope<T> message);
}
