package com.michael.chaos.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.web.support.ClientIpResolver;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Servlet trace 过滤器测试。
 */
class TraceFilterTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private static final String UPSTREAM_SPAN_ID = "00f067aa0ba902b7";

    /**
     * 每个用例结束后清理上下文，避免线程复用污染后续测试。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * trace 过滤器应解析 W3C 请求头并写回响应头。
     */
    @Test
    void shouldReadAndWriteW3cTraceHeaders() throws Exception {
        TraceFilter filter = new TraceFilter("order-service");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(ChaosHeaders.TRACEPARENT, TRACEPARENT);
        request.addHeader(ChaosHeaders.SPAN_ID, UPSTREAM_SPAN_ID);
        request.addHeader(ChaosHeaders.TRACESTATE, "vendor=state");
        request.addHeader(ChaosHeaders.BAGGAGE, "tenant=acme");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(TraceContext.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(TraceContext.spanId()).hasSize(16).isNotEqualTo(UPSTREAM_SPAN_ID);
            assertThat(TraceContext.traceState()).isEqualTo("vendor=state");
            assertThat(TraceContext.baggage()).isEqualTo("tenant=acme");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ChaosHeaders.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getHeader(ChaosHeaders.SPAN_ID)).hasSize(16);
        assertThat(response.getHeader(ChaosHeaders.SPAN_ID)).isNotEqualTo(UPSTREAM_SPAN_ID);
        assertThat(response.getHeader(ChaosHeaders.TRACEPARENT))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(response.getHeader(ChaosHeaders.TRACESTATE)).isEqualTo("vendor=state");
    }

    /**
     * 默认不信任身份请求头，伪造的 X-User-Id / X-Tenant-Id 不能进入上下文。
     */
    @Test
    void shouldIgnoreIdentityHeadersByDefault() throws Exception {
        TraceFilter filter = new TraceFilter("order-service");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(ChaosHeaders.USER_ID, "1");
        request.addHeader(ChaosHeaders.TENANT_ID, "tenant-b");
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            userId.set(RequestContext.userId());
            tenantId.set(RequestContext.tenantId());
        });

        assertThat(userId).hasValue("");
        assertThat(tenantId).hasValue("");
    }

    /**
     * 开启信任且直连对端命中可信代理时才采纳身份请求头；非可信来源仍然忽略。
     */
    @Test
    void shouldTrustIdentityHeadersOnlyFromTrustedProxy() throws Exception {
        TraceFilter filter = new TraceFilter(
                "order-service", new ClientIpResolver(List.of("10.0.0.0/8")), true, CurrentSpanProvider.NONE);
        AtomicReference<String> userId = new AtomicReference<>();

        MockHttpServletRequest trusted = new MockHttpServletRequest("GET", "/api/orders");
        trusted.setRemoteAddr("10.1.1.1");
        trusted.addHeader(ChaosHeaders.USER_ID, "1001");
        filter.doFilter(trusted, new MockHttpServletResponse(), (req, res) -> userId.set(RequestContext.userId()));
        assertThat(userId).hasValue("1001");

        MockHttpServletRequest untrusted = new MockHttpServletRequest("GET", "/api/orders");
        untrusted.setRemoteAddr("203.0.113.9");
        untrusted.addHeader(ChaosHeaders.USER_ID, "1001");
        filter.doFilter(untrusted, new MockHttpServletResponse(), (req, res) -> userId.set(RequestContext.userId()));
        assertThat(userId).hasValue("");
    }

    /**
     * 可信来源透传的非法租户 ID 仍然会被白名单拒绝，杜绝 SQL 注入。
     */
    @Test
    void shouldRejectMaliciousTenantEvenFromTrustedProxy() throws Exception {
        TraceFilter filter = new TraceFilter(
                "order-service", new ClientIpResolver(List.of("127.0.0.1")), true, CurrentSpanProvider.NONE);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(ChaosHeaders.TENANT_ID, "x' OR '1'='1");
        AtomicReference<String> tenantId = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> tenantId.set(RequestContext.tenantId()));

        assertThat(tenantId).hasValue("");
    }

    /**
     * 请求结束后应清理所有注册的线程上下文，防止线程复用串号。
     */
    @Test
    void shouldClearAllRegisteredContextsAfterRequest() throws Exception {
        ContextPropagation.register(new LeakyAccessor());
        TraceFilter filter = new TraceFilter("order-service");

        filter.doFilter(new MockHttpServletRequest("GET", "/api"), new MockHttpServletResponse(),
                (req, res) -> LEAKY.set("tenant-from-business-code"));

        assertThat(LEAKY.get()).isNull();
        assertThat(RequestContext.current()).isEmpty();
    }

    /**
     * 存在外部追踪系统时沿用其 traceId/spanId。
     */
    @Test
    void shouldAdoptExternalSpanWhenAvailable() throws Exception {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String spanId = "a1b2c3d4e5f60718";
        TraceFilter filter = new TraceFilter("svc", new ClientIpResolver(), false,
                () -> Optional.of(new CurrentSpanProvider.SpanIds(traceId, spanId)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addHeader(ChaosHeaders.TRACEPARENT, TRACEPARENT);

        filter.doFilter(request, response, (req, res) -> {
            assertThat(TraceContext.traceId()).isEqualTo(traceId);
            assertThat(TraceContext.spanId()).isEqualTo(spanId);
        });

        assertThat(response.getHeader(ChaosHeaders.TRACEPARENT)).isEqualTo("00-" + traceId + "-" + spanId + "-01");
    }

    /**
     * 非法旧版 X-Trace-Id 不能被原样写回响应头。
     */
    @Test
    void shouldRegenerateIllegalLegacyTraceId() throws Exception {
        TraceFilter filter = new TraceFilter("svc");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addHeader(ChaosHeaders.TRACE_ID, "<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(ChaosHeaders.TRACE_ID)).matches("[0-9a-f]{32}");
    }

    private static final ThreadLocal<String> LEAKY = new ThreadLocal<>();

    private static final class LeakyAccessor implements ContextAccessor<String> {

        @Override
        public String capture() {
            return LEAKY.get();
        }

        @Override
        public Scope restore(String snapshot) {
            String previous = LEAKY.get();
            if (snapshot == null) {
                LEAKY.remove();
            } else {
                LEAKY.set(snapshot);
            }
            return () -> {
                if (previous == null) {
                    LEAKY.remove();
                } else {
                    LEAKY.set(previous);
                }
            };
        }
    }
}
