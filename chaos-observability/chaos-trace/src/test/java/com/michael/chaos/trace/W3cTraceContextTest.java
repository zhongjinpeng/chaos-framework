package com.michael.chaos.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * W3C Trace Context 解析测试。
 */
class W3cTraceContextTest {

    /**
     * 合法 traceparent 应被解析为标准字段。
     */
    @Test
    void shouldParseValidTraceparent() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        W3cTraceContext context = W3cTraceContext.parse(traceparent).orElseThrow();

        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.spanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(context.traceFlags()).isEqualTo("01");
        assertThat(context.traceparent()).isEqualTo(traceparent);
    }

    /**
     * 大写 traceparent 应被规范化为小写。
     */
    @Test
    void shouldNormalizeUppercaseTraceparent() {
        W3cTraceContext context = W3cTraceContext
                .parse("00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01")
                .orElseThrow();

        assertThat(context.traceparent())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    /**
     * 全零 traceId 或 spanId 必须被拒绝。
     */
    @Test
    void shouldRejectAllZeroIdentifiers() {
        assertThat(W3cTraceContext.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01"))
                .isEmpty();
        assertThat(W3cTraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"))
                .isEmpty();
    }

    /**
     * 非法格式 traceparent 必须被拒绝。
     */
    @Test
    void shouldRejectInvalidTraceparent() {
        assertThat(W3cTraceContext.parse("invalid")).isEmpty();
        assertThat(W3cTraceContext.parse("00-short-00f067aa0ba902b7-01")).isEmpty();
        assertThat(W3cTraceContext.parse("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isEmpty();
    }

    /**
     * 更高版本的 traceparent 应按 00 版本解析前 4 段，忽略新增字段，保证前向兼容。
     */
    @Test
    void shouldParseFutureVersionForwardCompatibly() {
        assertThat(W3cTraceContext.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra"))
                .map(W3cTraceContext::traceId)
                .hasValue("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(W3cTraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra"))
                .isEmpty();
        assertThat(W3cTraceContext.parse("0g-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isEmpty();
    }
}
