package com.michael.chaos.trace;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.log.MdcKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Trace 请求头构建器。
 */
public final class TraceHeaders {

    private TraceHeaders() {
    }

    /**
     * 构建出站请求需要透传的 trace 请求头。
     *
     * <p>用户和租户请求头仅用于内网服务间透传，取值来自已经过白名单校验的上下文。
     * 下游服务默认不信任这两个请求头，只有显式配置可信代理后才会采纳。</p>
     */
    public static Map<String, String> outgoing() {
        Map<String, String> headers = new LinkedHashMap<>();
        String traceId = TraceContext.traceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.newId();
        }
        String spanId = TraceContext.newSpanId();
        TracePropagationContext propagationContext = TraceContext.nextOutgoingPropagationContext(spanId);
        if (propagationContext.w3cTraceContext() == null) {
            propagationContext = propagationContext.withW3cTraceContext(W3cTraceContext.from(traceId, spanId).orElse(null));
        }
        put(headers, ChaosHeaders.TRACE_ID, traceId);
        put(headers, ChaosHeaders.SPAN_ID, spanId);
        propagationContext.traceparent().ifPresent(value -> put(headers, ChaosHeaders.TRACEPARENT, value));
        propagationContext.traceStateValue().ifPresent(value -> put(headers, ChaosHeaders.TRACESTATE, value));
        propagationContext.baggageValue().ifPresent(value -> put(headers, ChaosHeaders.BAGGAGE, value));
        put(headers, ChaosHeaders.USER_ID, MDC.get(MdcKeys.USER_ID));
        put(headers, ChaosHeaders.TENANT_ID, MDC.get(MdcKeys.TENANT_ID));
        return headers;
    }

    /**
     * 构建当前请求响应需要返回的 trace 请求头，不创建新的子 span。
     */
    public static Map<String, String> current() {
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, ChaosHeaders.TRACE_ID, TraceContext.traceId());
        put(headers, ChaosHeaders.SPAN_ID, TraceContext.spanId());
        put(headers, ChaosHeaders.TRACEPARENT, TraceContext.traceparent());
        put(headers, ChaosHeaders.TRACESTATE, TraceContext.traceState());
        return headers;
    }

    /**
     * 仅写入非空请求头。
     */
    private static void put(Map<String, String> headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(key, value);
        }
    }
}
