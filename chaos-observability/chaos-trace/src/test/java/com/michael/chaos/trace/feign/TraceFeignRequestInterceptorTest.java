package com.michael.chaos.trace.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.TraceContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceFeignRequestInterceptorTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldPropagateW3cHeaders() {
        TraceContext.start("", "", TRACEPARENT, "vendor=state", "tenant=acme", "", "", "order-service");
        RequestTemplate template = new RequestTemplate();

        new TraceFeignRequestInterceptor().apply(template);

        assertThat(template.headers().get(ChaosHeaders.TRACE_ID))
                .containsExactly("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(template.headers().get(ChaosHeaders.SPAN_ID).iterator().next()).hasSize(16);
        assertThat(template.headers().get(ChaosHeaders.TRACEPARENT).iterator().next())
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
                .endsWith("-01");
        assertThat(template.headers().get(ChaosHeaders.TRACESTATE)).containsExactly("vendor=state");
        assertThat(template.headers().get(ChaosHeaders.BAGGAGE)).containsExactly("tenant=acme");
    }
}
