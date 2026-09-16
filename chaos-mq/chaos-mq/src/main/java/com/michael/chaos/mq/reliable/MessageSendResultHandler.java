package com.michael.chaos.mq.reliable;

/**
 * MQ 发送确认结果处理端口。
 */
@FunctionalInterface
public interface MessageSendResultHandler {

    /**
     * 处理发送确认结果。
     */
    void handle(MessageSendResult result);
}
