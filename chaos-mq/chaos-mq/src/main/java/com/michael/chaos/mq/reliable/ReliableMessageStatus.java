package com.michael.chaos.mq.reliable;

/**
 * 可靠消息状态。
 */
public enum ReliableMessageStatus {

    /**
     * 消息已保存到 outbox，等待发送。
     */
    PENDING,

    /**
     * 消息发送中。
     */
    SENDING,

    /**
     * 消息已被 MQ 确认。
     */
    SENT,

    /**
     * 消息发送失败，等待重试。
     */
    RETRYING,

    /**
     * 消息已超过重试上限，进入死信。
     */
    DEAD_LETTER,

    /**
     * 消息被人工或业务流程取消。
     */
    CANCELED
}
