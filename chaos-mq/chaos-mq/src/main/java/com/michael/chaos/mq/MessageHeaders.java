package com.michael.chaos.mq;

/**
 * 框架统一的消息头名称。
 *
 * <p>Kafka、RocketMQ 等适配器必须使用同一套消息头，消费端才能用一致的方式还原消息 ID、标签和上下文。</p>
 */
public final class MessageHeaders {

    /**
     * 框架消息 ID，消费端幂等以该值为准。
     */
    public static final String MESSAGE_ID = "message-id";

    /**
     * 消息标签。
     */
    public static final String MESSAGE_TAG = "message-tag";

    private MessageHeaders() {
    }
}
