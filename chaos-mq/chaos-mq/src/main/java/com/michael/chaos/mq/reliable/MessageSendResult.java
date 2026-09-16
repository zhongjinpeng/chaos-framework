package com.michael.chaos.mq.reliable;

import java.time.Instant;
import java.util.Objects;

/**
 * 消息发送确认结果。
 *
 * @param messageId 消息 ID
 * @param success 是否发送成功
 * @param brokerMessageId MQ broker 返回的消息 ID
 * @param reason 失败原因
 * @param confirmedAt 确认时间
 */
public record MessageSendResult(
        String messageId,
        boolean success,
        String brokerMessageId,
        String reason,
        Instant confirmedAt
) {

    /**
     * 规范化发送结果字段。
     */
    public MessageSendResult {
        messageId = Objects.requireNonNullElse(messageId, "").trim();
        brokerMessageId = Objects.requireNonNullElse(brokerMessageId, "").trim();
        reason = Objects.requireNonNullElse(reason, "").trim();
        confirmedAt = confirmedAt == null ? Instant.now() : confirmedAt;
    }

    /**
     * 创建成功确认结果。
     */
    public static MessageSendResult success(String messageId, String brokerMessageId) {
        return new MessageSendResult(messageId, true, brokerMessageId, "", Instant.now());
    }

    /**
     * 创建失败确认结果。
     */
    public static MessageSendResult failure(String messageId, String reason) {
        return new MessageSendResult(messageId, false, "", reason, Instant.now());
    }
}
