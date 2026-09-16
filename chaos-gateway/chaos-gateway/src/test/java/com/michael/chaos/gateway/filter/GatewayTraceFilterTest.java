package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Gateway trace 过滤器测试。
 */
class GatewayTraceFilterTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    /**
     * 请求缺少 traceId 时应自动补齐并写入响应头。
     */
    @Test
    void shouldCreateTraceHeadersWhenMissing() {
        GatewayTraceFilter filter = new GatewayTraceFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        StepVerifier.create(filter.filter(exchange, next -> {
            String traceId = next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACE_ID);
            String spanId = next.getRequest().getHeaders().getFirst(ChaosHeaders.SPAN_ID);
            assertThat(traceId).isNotBlank();
            assertThat(spanId).isNotBlank();
            next.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return next.getResponse().setComplete();
        })).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(ChaosHeaders.TRACE_ID)).isNotBlank();
    }

    /**
     * 请求已有 traceId 时应继续透传。
     */
    @Test
    void shouldPropagateExistingTraceId() {
        GatewayTraceFilter filter = new GatewayTraceFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(ChaosHeaders.TRACE_ID, "trace-1"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACE_ID)).isEqualTo("trace-1");
            return next.getResponse().setComplete();
        })).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(ChaosHeaders.TRACE_ID)).isEqualTo("trace-1");
    }

    /**
     * 请求已有 W3C traceparent 时应优先使用其中的 traceId，并透传 tracestate 和 baggage。
     */
    @Test
    void shouldPropagateW3cTraceContext() {
        GatewayTraceFilter filter = new GatewayTraceFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(ChaosHeaders.TRACEPARENT, TRACEPARENT)
                .header(ChaosHeaders.TRACESTATE, "vendor=state")
                .header(ChaosHeaders.BAGGAGE, "tenant=acme"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACE_ID))
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.SPAN_ID)).hasSize(16);
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACEPARENT))
                    .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
                    .endsWith("-01");
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACESTATE)).isEqualTo("vendor=state");
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.BAGGAGE)).isEqualTo("tenant=acme");
            next.getResponse().getHeaders().add(ChaosHeaders.TRACE_ID, "downstream-trace");
            next.getResponse().getHeaders().set(ChaosHeaders.SPAN_ID, "downstream-span");
            next.getResponse().getHeaders().set(ChaosHeaders.TRACEPARENT, TRACEPARENT);
            return next.getResponse().setComplete();
        })).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(ChaosHeaders.TRACE_ID))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(exchange.getResponse().getHeaders().get(ChaosHeaders.TRACE_ID)).hasSize(1);
        assertThat(exchange.getResponse().getHeaders().getFirst(ChaosHeaders.SPAN_ID)).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(ChaosHeaders.TRACEPARENT)).isNull();
    }

    /**
     * 非法格式的 X-Trace-Id 必须被丢弃并重新生成，防止响应头和日志注入。
     */
    @Test
    void shouldReplaceInvalidLegacyTraceId() {
        GatewayTraceFilter filter = new GatewayTraceFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header(ChaosHeaders.TRACE_ID, "bad trace id with spaces and a very long suffix that exceeds limits"));

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().getFirst(ChaosHeaders.TRACE_ID)).matches("[0-9a-f]{32}");
            return next.getResponse().setComplete();
        })).verifyComplete();
    }
}
