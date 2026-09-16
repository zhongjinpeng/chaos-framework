package com.michael.chaos.mq;

import com.michael.chaos.trace.TraceHeaders;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 框架标准消息信封。
 *
 * <p>业务 payload 通过该信封携带统一的主题、标签、请求头和创建时间，
 * 便于不同 MQ 实现保持一致的消息边界。</p>
 *
 * @param messageId 消息唯一标识
 * @param topic 消息主题
 * @param tag 消息标签
 * @param payload 业务消息体
 * @param headers 需要随消息透传的请求头
 * @param createdAt 消息创建时间
 * @param <T> 业务消息体类型
 */
public record MessageEnvelope<T>(
        String messageId,
        String topic,
        String tag,
        T payload,
        Map<String, String> headers,
        Instant createdAt
) {

    /**
     * 规范化消息信封默认值。
     */
    public MessageEnvelope {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /**
     * 合并当前请求 trace 头，显式传入的消息头优先级更高。
     */
    public MessageEnvelope<T> withTraceHeaders() {
        Map<String, String> mergedHeaders = new LinkedHashMap<>(TraceHeaders.outgoing());
        mergedHeaders.putAll(headers);
        return new MessageEnvelope<>(messageId, topic, tag, payload, mergedHeaders, createdAt);
    }
}
