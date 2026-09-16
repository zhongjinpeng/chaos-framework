package com.michael.chaos.web.filter;

import java.util.Optional;

/**
 * 外部追踪系统当前 span 的提供者。
 *
 * <p>应用引入 Micrometer Tracing（Brave / OpenTelemetry bridge）后，服务端 span 由 Boot 的
 * Observation 过滤器创建，并以 {@code traceId}/{@code spanId} 写入 MDC。框架如果再独立生成一套 ID，
 * 两者会互相覆盖。{@link TraceFilter} 通过该接口优先采用外部 span 的 ID，保证日志、响应头和
 * {@code Result.traceId} 与 APM 平台一致。</p>
 */
@FunctionalInterface
public interface CurrentSpanProvider {

    /**
     * 不提供外部 span 的默认实现。
     */
    CurrentSpanProvider NONE = Optional::empty;

    /**
     * 返回当前线程上活跃的外部 span。
     *
     * @return 外部 span 标识；不存在时返回空
     */
    Optional<SpanIds> currentSpan();

    /**
     * 外部 span 标识。
     *
     * @param traceId trace ID
     * @param spanId span ID
     */
    record SpanIds(String traceId, String spanId) {
    }
}
