package com.michael.chaos.trace;

import com.michael.chaos.core.context.RequestContextSnapshot;
import java.util.Map;
import java.util.Objects;

/**
 * 当前线程上下文快照。
 *
 * <p>用于把 trace、请求上下文和 MDC 从提交任务的线程传播到执行任务的线程。</p>
 *
 * @param requestContext 请求上下文
 * @param propagationContext trace 传播上下文
 * @param mdcContext MDC 上下文
 */
public record TraceContextSnapshot(
        RequestContextSnapshot requestContext,
        TracePropagationContext propagationContext,
        Map<String, String> mdcContext
) {

    /**
     * 规范化上下文字段。
     */
    public TraceContextSnapshot {
        propagationContext = propagationContext == null ? TracePropagationContext.empty() : propagationContext;
        mdcContext = mdcContext == null ? Map.of() : Map.copyOf(mdcContext);
    }

    /**
     * 是否为空上下文。
     */
    public boolean isEmpty() {
        return requestContext == null
                && propagationContext.w3cTraceContext() == null
                && propagationContext.traceState().isBlank()
                && propagationContext.baggage().isBlank()
                && mdcContext.isEmpty();
    }

    /**
     * 创建空上下文快照。
     */
    public static TraceContextSnapshot empty() {
        return new TraceContextSnapshot(null, TracePropagationContext.empty(), Map.of());
    }

    /**
     * 创建指定请求上下文的快照。
     */
    public static TraceContextSnapshot of(RequestContextSnapshot requestContext) {
        return new TraceContextSnapshot(
                Objects.requireNonNull(requestContext, "requestContext must not be null"),
                TracePropagationContext.empty(),
                Map.of()
        );
    }
}
