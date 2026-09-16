package com.michael.chaos.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * W3C baggage 解析测试。
 */
class TraceBaggageTest {

    /**
     * baggage 应解析合法键值并忽略属性段。
     */
    @Test
    void shouldParseBaggageEntries() {
        TraceBaggage baggage = TraceBaggage.parse("tenant=acme, gray=beta;property=value");

        assertThat(baggage.entries())
                .containsEntry("tenant", "acme")
                .containsEntry("gray", "beta");
        assertThat(baggage.headerValue()).isEqualTo("tenant=acme,gray=beta");
    }

    /**
     * 非法 key 和包含分隔符的 value 应被过滤。
     */
    @Test
    void shouldFilterInvalidEntries() {
        TraceBaggage baggage = TraceBaggage.of(Map.of(
                "valid_key", "value",
                "bad key", "value",
                "bad-value", "a,b"
        ));

        assertThat(baggage.entries()).containsOnly(Map.entry("valid_key", "value"));
    }
}
