package com.michael.chaos.trace;

import java.util.Objects;
import java.util.Optional;

/**
 * Trace 跨进程传播上下文。
 *
 * <p>保存当前请求解析得到的 W3C Trace Context、tracestate 和 baggage，供 Feign、MQ
 * 等出站通道继续透传。</p>
 *
 * @param w3cTraceContext W3C trace 上下文
 * @param traceState W3C tracestate 原始值
 * @param baggage W3C baggage 原始值
 */
public record TracePropagationContext(
        W3cTraceContext w3cTraceContext,
        String traceState,
        String baggage
) {

    /**
     * 规范化请求头值，移除换行符避免响应头注入。
     */
    public TracePropagationContext {
        traceState = sanitize(traceState);
        baggage = sanitize(baggage);
    }

    /**
     * 创建空传播上下文。
     */
    public static TracePropagationContext empty() {
        return new TracePropagationContext(null, "", "");
    }

    /**
     * 获取 traceparent 请求头值。
     */
    public Optional<String> traceparent() {
        return w3cTraceContext == null ? Optional.empty() : Optional.of(w3cTraceContext.traceparent());
    }

    /**
     * 获取 tracestate 请求头值。
     */
    public Optional<String> traceStateValue() {
        return traceState.isBlank() ? Optional.empty() : Optional.of(traceState);
    }

    /**
     * 获取 baggage 请求头值。
     */
    public Optional<String> baggageValue() {
        return baggage.isBlank() ? Optional.empty() : Optional.of(baggage);
    }

    /**
     * 使用新的 W3C 上下文替换 traceparent，保留 tracestate 和 baggage。
     */
    public TracePropagationContext withW3cTraceContext(W3cTraceContext context) {
        return new TracePropagationContext(context, traceState, baggage);
    }

    /**
     * 移除控制字符并限制长度。
     *
     * <p>W3C 规定 tracestate 不超过 512 字符、baggage 不超过 8192 字节；这里统一使用 8192 作为上限，
     * 超长值直接丢弃而非截断，避免截断后产生语义错误的传播值。</p>
     */
    private static String sanitize(String value) {
        String sanitized = Objects.requireNonNullElse(value, "")
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        return sanitized.length() > MAX_HEADER_LENGTH ? "" : sanitized;
    }

    private static final int MAX_HEADER_LENGTH = 8192;
}
