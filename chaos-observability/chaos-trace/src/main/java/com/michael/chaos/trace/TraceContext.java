package com.michael.chaos.trace;

import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextIdentifiers;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.trace.log.MdcKeys;
import com.michael.chaos.trace.log.MdcSupport;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * Trace 上下文入口。
 *
 * <p>负责创建 traceId/spanId，维护 {@link RequestContext}，并同步写入 MDC。</p>
 */
public final class TraceContext {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ThreadLocal<TracePropagationContext> PROPAGATION_CONTEXT = new ThreadLocal<>();

    private static final ContextAccessor<TraceContextSnapshot> ACCESSOR = new ContextAccessor<>() {
        @Override
        public TraceContextSnapshot capture() {
            return TraceContext.capture();
        }

        @Override
        public Scope restore(TraceContextSnapshot snapshot) {
            TraceContext.Scope inner = TraceContext.restore(snapshot);
            return inner::close;
        }

        /**
         * 请求边界清理时只移除框架维护的 MDC 字段，保留其他组件写入的 MDC。
         */
        @Override
        public void clear() {
            TraceContext.clear();
        }
    };

    static {
        ContextPropagation.register(ACCESSOR);
    }

    private TraceContext() {
    }

    /**
     * 获取当前 traceId。
     */
    public static String traceId() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        return traceId == null || traceId.isBlank()
                ? RequestContext.current().map(RequestContextSnapshot::traceId).orElse("")
                : traceId;
    }

    /**
     * 获取当前 spanId。
     */
    public static String spanId() {
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        return spanId == null || spanId.isBlank()
                ? RequestContext.current().map(RequestContextSnapshot::spanId).orElse("")
                : spanId;
    }

    /**
     * 开始一次请求上下文。
     *
     * @param traceId 上游 traceId，空值时自动生成
     * @param spanId 上游 spanId，空值时自动生成
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param appName 当前应用名称
     * @return 已创建的请求上下文快照
     */
    public static RequestContextSnapshot start(
            String traceId,
            String spanId,
            String tenantId,
            String userId,
            String appName) {
        return start(traceId, spanId, null, null, null, tenantId, userId, appName);
    }

    /**
     * 开始一次服务端请求上下文。
     *
     * <p>只继承上游 traceId、采样标志和传播状态，始终为当前服务生成新的 spanId。
     * 上游 spanId 通过 traceparent 表达父子关系，不能复用为当前服务 spanId。</p>
     *
     * @param traceId 兼容旧系统的 traceId 请求头
     * @param traceparent W3C traceparent 请求头
     * @param traceState W3C tracestate 请求头
     * @param baggage W3C baggage 请求头
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param appName 当前应用名称
     * @return 已创建的服务端请求上下文快照
     */
    public static RequestContextSnapshot startServer(
            String traceId,
            String traceparent,
            String traceState,
            String baggage,
            String tenantId,
            String userId,
            String appName) {
        return start(
                traceId,
                null,
                traceparent,
                traceState,
                baggage,
                tenantId,
                userId,
                appName);
    }

    /**
     * 开始一次支持 W3C Trace Context 的请求上下文。
     *
     * @param traceId 兼容旧系统的 traceId 请求头
     * @param spanId 兼容旧系统的 spanId 请求头
     * @param traceparent W3C traceparent 请求头
     * @param traceState W3C tracestate 请求头
     * @param baggage W3C baggage 请求头
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param appName 当前应用名称
     * @return 已创建的请求上下文快照
     */
    public static RequestContextSnapshot start(
            String traceId,
            String spanId,
            String traceparent,
            String traceState,
            String baggage,
            String tenantId,
            String userId,
            String appName) {
        W3cTraceContext upstreamW3cContext = W3cTraceContext.parse(traceparent).orElse(null);
        String resolvedTraceId = resolveTraceId(traceId, upstreamW3cContext);
        String resolvedSpanId = normalizeId(spanId, newSpanId());
        RequestContextSnapshot snapshot = new RequestContextSnapshot(
                resolvedTraceId,
                resolvedSpanId,
                tenantId,
                userId,
                appName
        );
        W3cTraceContext currentW3cContext = W3cTraceContext.from(
                resolvedTraceId,
                resolvedSpanId,
                upstreamW3cContext == null ? W3cTraceContext.SAMPLED_FLAGS : upstreamW3cContext.traceFlags()
        ).orElse(null);
        bind(snapshot, new TracePropagationContext(currentW3cContext, traceState, baggage));
        return snapshot;
    }

    /**
     * 直接采用外部追踪系统（例如 Micrometer Tracing / OpenTelemetry）已经创建的当前 span。
     *
     * <p>与 {@link #startServer} 不同，该方法不会再生成新的 spanId：当应用引入了 Micrometer Tracing 时，
     * 服务端 span 已由 Observation 过滤器创建，框架如果再独立生成一套 traceId/spanId，
     * 会与 Micrometer 写入 MDC 的同名字段互相覆盖，导致日志和 {@code Result.traceId} 对不上。</p>
     *
     * @param currentSpan 外部追踪系统当前 span 的 W3C 表示
     * @param traceState W3C tracestate 请求头
     * @param baggage W3C baggage 请求头
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param appName 当前应用名称
     * @return 已创建的请求上下文快照
     */
    public static RequestContextSnapshot adopt(
            W3cTraceContext currentSpan,
            String traceState,
            String baggage,
            String tenantId,
            String userId,
            String appName) {
        Objects.requireNonNull(currentSpan, "currentSpan must not be null");
        RequestContextSnapshot snapshot = new RequestContextSnapshot(
                currentSpan.traceId(),
                currentSpan.spanId(),
                tenantId,
                userId,
                appName
        );
        bind(snapshot, new TracePropagationContext(currentSpan, traceState, baggage));
        return snapshot;
    }

    /**
     * 把请求快照和传播上下文绑定到当前线程。
     *
     * <p>MDC 中的租户和用户字段取自已经过白名单校验的快照，而不是调用方传入的原始值，
     * 防止换行符等控制字符进入日志。</p>
     */
    private static void bind(RequestContextSnapshot snapshot, TracePropagationContext propagationContext) {
        RequestContext.set(snapshot);
        MdcSupport.putIfNotBlank(MdcKeys.TRACE_ID, snapshot.traceId());
        MdcSupport.putIfNotBlank(MdcKeys.SPAN_ID, snapshot.spanId());
        MdcSupport.putIfNotBlank(MdcKeys.TENANT_ID, snapshot.tenantId());
        MdcSupport.putIfNotBlank(MdcKeys.USER_ID, snapshot.userId());
        MdcSupport.putIfNotBlank(MdcKeys.APP_NAME, snapshot.appName());
        PROPAGATION_CONTEXT.set(propagationContext);
    }

    /**
     * 捕获当前线程上下文快照。
     *
     * @return 当前线程上下文快照
     */
    public static TraceContextSnapshot capture() {
        return new TraceContextSnapshot(
                RequestContext.current().orElse(null),
                propagationContext(),
                MdcSupport.copy()
        );
    }

    /**
     * 在当前线程恢复指定上下文，并返回可关闭作用域。
     *
     * <p>关闭作用域时会恢复进入作用域前的上下文，适合在线程池、异步任务和消息消费中使用。</p>
     *
     * @param snapshot 要恢复的上下文快照
     * @return 上下文作用域
     */
    public static Scope restore(TraceContextSnapshot snapshot) {
        TraceContextSnapshot previous = capture();
        apply(snapshot == null ? TraceContextSnapshot.empty() : snapshot);
        return new Scope(previous);
    }

    /**
     * 使用当前上下文包装任务。
     *
     * @param delegate 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        TraceContextSnapshot snapshot = capture();
        return () -> {
            try (Scope ignored = restore(snapshot)) {
                delegate.run();
            }
        };
    }

    /**
     * 清理请求上下文和框架 MDC 字段。
     */
    public static void clear() {
        RequestContext.clear();
        PROPAGATION_CONTEXT.remove();
        MdcSupport.clearFrameworkKeys();
    }

    /**
     * 生成 128 位 traceId。
     */
    public static String newId() {
        return randomNonZeroHex(16);
    }

    /**
     * 生成 64 位 spanId。
     */
    public static String newSpanId() {
        return randomNonZeroHex(8);
    }

    /**
     * 获取当前 traceparent 请求头值。
     */
    public static String traceparent() {
        TracePropagationContext context = propagationContext();
        if (context.w3cTraceContext() != null) {
            return context.w3cTraceContext().traceparent();
        }
        return W3cTraceContext.from(traceId(), spanId())
                .map(W3cTraceContext::traceparent)
                .orElse("");
    }

    /**
     * 获取当前 tracestate 请求头值。
     */
    public static String traceState() {
        return propagationContext().traceStateValue().orElse("");
    }

    /**
     * 获取当前 baggage 请求头值。
     */
    public static String baggage() {
        return propagationContext().baggageValue().orElse("");
    }

    /**
     * 获取当前传播上下文。
     */
    public static TracePropagationContext propagationContext() {
        TracePropagationContext context = PROPAGATION_CONTEXT.get();
        return context == null ? TracePropagationContext.empty() : context;
    }

    /**
     * 为出站调用创建新的子 span 传播上下文。
     */
    static TracePropagationContext nextOutgoingPropagationContext(String outgoingSpanId) {
        TracePropagationContext current = propagationContext();
        W3cTraceContext outgoingW3cContext = W3cTraceContext.from(traceId(), outgoingSpanId)
                .orElse(null);
        return current.withW3cTraceContext(outgoingW3cContext);
    }

    private static void apply(TraceContextSnapshot snapshot) {
        Optional.ofNullable(snapshot.requestContext()).ifPresentOrElse(RequestContext::set, RequestContext::clear);
        TracePropagationContext propagationContext = snapshot.propagationContext() == null
                ? TracePropagationContext.empty()
                : snapshot.propagationContext();
        if (isEmptyPropagationContext(propagationContext)) {
            PROPAGATION_CONTEXT.remove();
        } else {
            PROPAGATION_CONTEXT.set(propagationContext);
        }
        MdcSupport.restore(snapshot.mdcContext());
    }

    private static boolean isEmptyPropagationContext(TracePropagationContext context) {
        return context.w3cTraceContext() == null
                && context.traceState().isBlank()
                && context.baggage().isBlank();
    }

    /**
     * 当前线程上下文作用域。
     */
    public static final class Scope implements AutoCloseable {

        private final TraceContextSnapshot previous;

        private boolean closed;

        private Scope(TraceContextSnapshot previous) {
            this.previous = previous;
        }

        /**
         * 恢复进入作用域前的上下文。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            apply(previous);
            closed = true;
        }
    }

    /**
     * 规范化兼容旧系统的 traceId/spanId。
     *
     * <p>外部传入的值会原样写回响应头、MDC 和访问日志，因此只接受字母数字、下划线和中划线且限制长度，
     * 非法值直接丢弃并重新生成，避免响应头拆分与日志注入。</p>
     */
    private static String normalizeId(String value, String defaultValue) {
        String sanitized = ContextIdentifiers.sanitizeTraceId(value);
        return sanitized.isEmpty() ? defaultValue : sanitized;
    }

    private static String resolveTraceId(String legacyTraceId, W3cTraceContext upstreamW3cContext) {
        if (upstreamW3cContext != null) {
            return upstreamW3cContext.traceId();
        }
        return normalizeId(legacyTraceId, newId());
    }

    private static String randomNonZeroHex(int byteLength) {
        String value;
        do {
            byte[] bytes = new byte[byteLength];
            RANDOM.nextBytes(bytes);
            value = HexFormat.of().formatHex(bytes);
        } while (value.chars().allMatch(ch -> ch == '0'));
        return value;
    }
}
