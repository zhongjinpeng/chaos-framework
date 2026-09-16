package com.michael.chaos.core.constant;

/**
 * chaos 模块使用的标准 HTTP 与消息请求头。
 */
public final class ChaosHeaders {

    /**
     * 链路追踪 ID 请求头。
     */
    public static final String TRACE_ID = "X-Trace-Id";

    /**
     * 当前调用跨度 ID 请求头。
     */
    public static final String SPAN_ID = "X-Span-Id";

    /**
     * W3C Trace Context 标准 traceparent 请求头。
     */
    public static final String TRACEPARENT = "traceparent";

    /**
     * W3C Trace Context 标准 tracestate 请求头。
     */
    public static final String TRACESTATE = "tracestate";

    /**
     * W3C Baggage 标准上下文请求头。
     */
    public static final String BAGGAGE = "baggage";

    /**
     * 当前用户 ID 请求头。
     */
    public static final String USER_ID = "X-User-Id";

    /**
     * 当前租户 ID 请求头。
     */
    public static final String TENANT_ID = "X-Tenant-Id";

    /**
     * 灰度路由标签请求头。
     */
    public static final String GRAY_TAG = "X-Gray-Tag";

    /**
     * 幂等请求键请求头。
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /**
     * 幂等响应回放标记响应头。
     *
     * <p>取值为 {@code true} 时表示本次响应是首次请求结果的回放，服务端没有重新执行业务逻辑。
     * 客户端可以据此区分"真的又执行了一次"和"命中幂等回放"。</p>
     */
    public static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    /**
     * 请求 ID 请求头，通常由负载均衡或客户端生成，仅用于日志关联。
     */
    public static final String REQUEST_ID = "X-Request-Id";

    /**
     * 代理转发的客户端 IP 链路请求头。
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ChaosHeaders() {
    }
}
