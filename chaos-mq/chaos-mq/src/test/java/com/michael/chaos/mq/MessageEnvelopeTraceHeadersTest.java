package com.michael.chaos.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.TraceContext;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 消息信封 trace 请求头测试。
 */
class MessageEnvelopeTraceHeadersTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    /**
     * 每个用例结束后清理上下文。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 消息信封应合并当前 trace 请求头。
     */
    @Test
    void shouldMergeTraceHeaders() {
        TraceContext.start("", "", TRACEPARENT, "vendor=state", "tenant=acme", "", "", "order-service");
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "message-1",
                "order-topic",
                "",
                "payload",
                Map.of("business-key", "order-1"),
                Instant.now()
        );

        MessageEnvelope<String> tracedEnvelope = envelope.withTraceHeaders();

        assertThat(tracedEnvelope.headers()).containsEntry("business-key", "order-1");
        assertThat(tracedEnvelope.headers().get(ChaosHeaders.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(tracedEnvelope.headers().get(ChaosHeaders.TRACEPARENT))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-")
                .endsWith("-01");
        assertThat(tracedEnvelope.headers().get(ChaosHeaders.BAGGAGE)).isEqualTo("tenant=acme");
    }

    /**
     * 显式消息头优先级应高于自动生成的 trace 头。
     */
    @Test
    void shouldKeepExplicitHeadersFirst() {
        TraceContext.start("", "", TRACEPARENT, "", "", "", "", "order-service");
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "message-1",
                "order-topic",
                "",
                "payload",
                Map.of(ChaosHeaders.TRACE_ID, "custom-trace"),
                Instant.now()
        );

        MessageEnvelope<String> tracedEnvelope = envelope.withTraceHeaders();

        assertThat(tracedEnvelope.headers()).containsEntry(ChaosHeaders.TRACE_ID, "custom-trace");
    }
}
