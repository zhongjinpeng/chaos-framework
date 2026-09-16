package com.michael.chaos.mq.reliable;

/**
 * 死信消息处理端口。
 */
@FunctionalInterface
public interface DeadLetterMessageHandler {

    /**
     * 处理死信消息。
     */
    void handle(ReliableMessage message);
}
