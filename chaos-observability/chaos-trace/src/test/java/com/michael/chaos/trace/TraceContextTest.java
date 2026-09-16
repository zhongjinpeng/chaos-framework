package com.michael.chaos.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.trace.log.MdcKeys;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Trace 上下文测试。
 */
class TraceContextTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    /**
     * 每个用例结束后清理线程上下文。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * W3C traceparent 优先决定 traceId，同时保留 tracestate 和 baggage。
     */
    @Test
    void shouldStartFromW3cTraceparent() {
        TraceContext.start(
                "legacy-trace",
                "",
                TRACEPARENT,
                "vendor=state",
                "tenant=acme",
                "tenant-1",
                "user-1",
                "order-service"
        );

        assertThat(TraceContext.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(TraceContext.traceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(TraceContext.traceState()).isEqualTo("vendor=state");
        assertThat(TraceContext.baggage()).isEqualTo("tenant=acme");
    }

    /**
     * 服务端入口必须继承 traceId，但不能复用上游 spanId。
     */
    @Test
    void shouldCreateIndependentServerSpan() {
        String upstreamSpanId = "00f067aa0ba902b7";

        TraceContext.startServer(
                "legacy-trace",
                TRACEPARENT,
                "vendor=state",
                "tenant=acme",
                "tenant-1",
                "user-1",
                "order-service"
        );

        assertThat(TraceContext.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(TraceContext.spanId())
                .hasSize(16)
                .isNotEqualTo(upstreamSpanId);
        assertThat(TraceContext.traceparent())
                .contains("-" + TraceContext.spanId() + "-")
                .doesNotContain("-" + upstreamSpanId + "-");
    }

    /**
     * 出站请求头应同时包含 legacy header 和 W3C header。
     */
    @Test
    void shouldBuildOutgoingHeadersWithW3cTraceContext() {
        TraceContext.start(
                "",
                "",
                TRACEPARENT,
                "vendor=state",
                "tenant=acme",
                "",
                "",
                "order-service"
        );

        Map<String, String> headers = TraceHeaders.outgoing();

        assertThat(headers.get(ChaosHeaders.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(headers.get(ChaosHeaders.SPAN_ID)).hasSize(16);
        assertThat(headers.get(ChaosHeaders.TRACEPARENT))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
                .endsWith("-01");
        assertThat(headers.get(ChaosHeaders.TRACESTATE)).isEqualTo("vendor=state");
        assertThat(headers.get(ChaosHeaders.BAGGAGE)).isEqualTo("tenant=acme");
    }

    /**
     * 没有当前上下文时，出站请求头应创建完整的新 trace。
     */
    @Test
    void shouldCreateTraceForOutgoingWithoutCurrentContext() {
        Map<String, String> headers = TraceHeaders.outgoing();

        assertThat(headers.get(ChaosHeaders.TRACE_ID)).hasSize(32);
        assertThat(headers.get(ChaosHeaders.SPAN_ID)).hasSize(16);
        assertThat(headers.get(ChaosHeaders.TRACEPARENT))
                .startsWith("00-" + headers.get(ChaosHeaders.TRACE_ID) + "-")
                .endsWith("-01");
    }

    /**
     * 包装异步任务时应传播提交线程上下文。
     */
    @Test
    void shouldWrapRunnableWithCapturedContext() {
        TraceContext.start("trace-main", "span-main", "", "", "order-service");
        MDC.put(MdcKeys.URI, "/orders");
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();
        AtomicReference<String> uri = new AtomicReference<>();

        Runnable wrapped = TraceContext.wrap(() -> {
            traceId.set(TraceContext.traceId());
            tenantId.set(RequestContext.tenantId());
            uri.set(MDC.get(MdcKeys.URI));
        });
        TraceContext.clear();

        wrapped.run();

        assertThat(traceId).hasValue("trace-main");
        assertThat(tenantId).hasValue("");
        assertThat(uri).hasValue("/orders");
        assertThat(TraceContext.traceId()).isBlank();
    }

    /**
     * 子线程不应隐式继承提交线程上下文，必须通过 wrap 或 TaskDecorator 显式传播。
     */
    @Test
    void shouldNotInheritContextToChildThreadImplicitly() throws Exception {
        TraceContext.start("trace-parent", "span-parent", "tenant-a", "user-a", "order-service");
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();

        Thread child = new Thread(() -> {
            traceId.set(TraceContext.traceId());
            tenantId.set(RequestContext.tenantId());
        });
        child.start();
        child.join();

        assertThat(traceId).hasValue("");
        assertThat(tenantId).hasValue("");
    }

    /**
     * 作用域关闭时应恢复执行线程进入作用域前的上下文。
     */
    @Test
    void shouldRestorePreviousContextWhenScopeClosed() {
        TraceContext.start("trace-previous", "span-previous", "tenant-a", "user-a", "service-a");
        TraceContextSnapshot snapshot = TraceContext.capture();
        TraceContext.start("trace-current", "span-current", "tenant-b", "user-b", "service-b");

        try (TraceContext.Scope ignored = TraceContext.restore(snapshot)) {
            assertThat(TraceContext.traceId()).isEqualTo("trace-previous");
            assertThat(RequestContext.tenantId()).isEqualTo("tenant-a");
        }

        assertThat(TraceContext.traceId()).isEqualTo("trace-current");
        assertThat(RequestContext.tenantId()).isEqualTo("tenant-b");
    }

    /**
     * 非法的旧版 traceId/spanId 和租户、用户 ID 不能进入 MDC 与响应头，防止头注入和 SQL 注入。
     */
    @Test
    void shouldRejectIllegalLegacyIdsAndIdentities() {
        TraceContext.start("trace\r\nSet-Cookie: a=b", "span id", "x' OR '1'='1", "user\n1", "svc");

        assertThat(TraceContext.traceId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(TraceContext.spanId()).hasSize(16);
        assertThat(RequestContext.tenantId()).isEmpty();
        assertThat(RequestContext.userId()).isEmpty();
        assertThat(MDC.get(MdcKeys.TENANT_ID)).isNull();
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }

    /**
     * 采用外部追踪系统 span 时，traceId 和 spanId 必须与外部保持一致，不再重新生成。
     */
    @Test
    void shouldAdoptExternalSpan() {
        W3cTraceContext external = W3cTraceContext.parse(TRACEPARENT).orElseThrow();

        TraceContext.adopt(external, "", "", "tenant-a", "user-a", "svc");

        assertThat(TraceContext.traceId()).isEqualTo(external.traceId());
        assertThat(TraceContext.spanId()).isEqualTo(external.spanId());
        assertThat(TraceContext.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isEqualTo(external.traceId());
    }

    /**
     * 通过统一传播中心清理时，只移除框架 MDC 字段，保留业务自定义字段。
     */
    @Test
    void clearAllShouldKeepNonFrameworkMdcKeys() {
        TraceContext.start("trace-a", "span-a", "tenant-a", "user-a", "svc");
        MDC.put("custom", "value");

        com.michael.chaos.core.context.ContextPropagation.clearAll();

        assertThat(RequestContext.current()).isEmpty();
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
        assertThat(MDC.get("custom")).isEqualTo("value");
        MDC.remove("custom");
    }
}
