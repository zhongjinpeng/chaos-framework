package com.michael.chaos.mq;

/**
 * 消息发送失败异常。
 *
 * <p>发布器必须在 broker 未确认、超时或序列化失败时抛出该异常，而不是静默返回。
 * outbox 派发器依赖异常判断是否需要重试，静默失败会导致消息被错误标记为 SENT。</p>
 */
public class MessagePublishException extends RuntimeException {

    /**
     * 创建消息发送失败异常。
     */
    public MessagePublishException(String message) {
        super(message);
    }

    /**
     * 创建带原因的消息发送失败异常。
     */
    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
