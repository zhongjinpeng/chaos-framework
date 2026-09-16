package com.michael.chaos.mq.reliable;

import com.michael.chaos.mq.MessageEnvelope;
import java.time.Instant;
import java.util.Objects;

/**
 * outbox 中的可靠消息。
 *
 * @param message 消息信封
 * @param status 可靠消息状态
 * @param retryTimes 已重试次数（发送失败和发送超时回收都会累加）
 * @param nextRetryAt 下次可重试时间
 * @param lastError 最近一次错误信息，超过 {@link #MAX_LAST_ERROR_LENGTH} 会被截断
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ReliableMessage(
        MessageEnvelope<?> message,
        ReliableMessageStatus status,
        int retryTimes,
        Instant nextRetryAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 错误信息最大长度，与 outbox 表 {@code last_error varchar(1024)} 保持一致。
     *
     * <p>不截断时，超长异常信息在 MySQL/PostgreSQL 严格模式下会让状态更新失败，
     * 消息会一直停在 SENDING 并被反复回收，永远进不了死信。</p>
     */
    public static final int MAX_LAST_ERROR_LENGTH = 1024;

    /**
     * 规范化可靠消息字段。
     */
    public ReliableMessage {
        message = Objects.requireNonNull(message, "message must not be null");
        status = Objects.requireNonNullElse(status, ReliableMessageStatus.PENDING);
        retryTimes = Math.max(retryTimes, 0);
        lastError = truncateError(lastError);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    /**
     * 创建待发送可靠消息。
     */
    public static ReliableMessage pending(MessageEnvelope<?> message) {
        Instant now = message.createdAt() == null ? Instant.now() : message.createdAt();
        return new ReliableMessage(message, ReliableMessageStatus.PENDING, 0, now, "", now, now);
    }

    /**
     * 截断错误信息，保证能写入 outbox 表。
     */
    public static String truncateError(String error) {
        String value = Objects.requireNonNullElse(error, "");
        return value.length() <= MAX_LAST_ERROR_LENGTH ? value : value.substring(0, MAX_LAST_ERROR_LENGTH);
    }

    /**
     * 标记为发送中。
     */
    public ReliableMessage sending() {
        return sending(Instant.now());
    }

    /**
     * 按指定时间标记为发送中。
     */
    public ReliableMessage sending(Instant now) {
        return new ReliableMessage(message, ReliableMessageStatus.SENDING, retryTimes, nextRetryAt, lastError, createdAt, now);
    }

    /**
     * 发送超时后被重新抢占。
     *
     * <p>回收同样计入重试次数：如果每次发送都卡住，消息最终会进入死信，而不是被无限回收。</p>
     */
    public ReliableMessage reclaimed(Instant now) {
        return new ReliableMessage(
                message,
                ReliableMessageStatus.SENDING,
                retryTimes + 1,
                nextRetryAt,
                "claim timeout, reclaimed",
                createdAt,
                now
        );
    }

    /**
     * 标记为已发送。
     */
    public ReliableMessage sent() {
        return new ReliableMessage(message, ReliableMessageStatus.SENT, retryTimes, nextRetryAt, "", createdAt, Instant.now());
    }

    /**
     * 标记为等待重试。
     */
    public ReliableMessage retrying(Instant nextRetryAt, String error) {
        return new ReliableMessage(
                message,
                ReliableMessageStatus.RETRYING,
                retryTimes + 1,
                nextRetryAt,
                error,
                createdAt,
                Instant.now()
        );
    }

    /**
     * 标记为死信。
     */
    public ReliableMessage deadLetter(String error) {
        return new ReliableMessage(
                message,
                ReliableMessageStatus.DEAD_LETTER,
                retryTimes,
                nextRetryAt,
                error,
                createdAt,
                Instant.now()
        );
    }
}
