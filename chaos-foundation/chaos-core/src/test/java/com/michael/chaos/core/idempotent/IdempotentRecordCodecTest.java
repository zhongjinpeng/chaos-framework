package com.michael.chaos.core.idempotent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 幂等响应快照编解码测试。
 */
class IdempotentRecordCodecTest {

    /**
     * 编码后再解码应完全还原，包括含中文的响应体和多个响应头。
     */
    @Test
    void shouldRoundTrip() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Location", "/orders/1001");
        headers.put("X-Custom", "a|b,c:d");
        IdempotentRecord record = new IdempotentRecord(
                201,
                "application/json;charset=UTF-8",
                "{\"id\":1001,\"name\":\"订单\"}".getBytes(StandardCharsets.UTF_8),
                headers,
                1_700_000_000_000L);

        IdempotentRecord decoded = IdempotentRecordCodec.decode(IdempotentRecordCodec.encode(record));

        assertEquals(record, decoded);
        assertTrue(new String(decoded.body(), StandardCharsets.UTF_8).contains("订单"));
        assertEquals(headers, decoded.headers());
    }

    /**
     * 分隔符只能出现在结构位置：响应头值里的 {@code |}、{@code ,}、{@code :} 经过 Base64 后不会破坏切分。
     */
    @Test
    void shouldNotBreakOnSeparatorsInsideValues() {
        String encoded = IdempotentRecordCodec.encode(
                new IdempotentRecord(200, "text/plain", "x".getBytes(StandardCharsets.UTF_8),
                        Map.of("X-A", "1|2,3:4"), 1L));

        assertEquals(5, encoded.chars().filter(ch -> ch == '|').count());
        assertEquals("1|2,3:4", IdempotentRecordCodec.decode(encoded).headers().get("X-A"));
    }

    /**
     * 没有响应头、没有响应体、没有内容类型时也能还原。
     *
     * <p>末尾的响应头字段为空字符串，用 {@code String.split} 会被丢弃导致字段数不足，这里专门覆盖。</p>
     */
    @Test
    void shouldRoundTripEmptyFields() {
        IdempotentRecord decoded = IdempotentRecordCodec.decode(
                IdempotentRecordCodec.encode(new IdempotentRecord(204, null, null, null, 7L)));

        assertEquals(204, decoded.statusCode());
        assertNull(decoded.contentType());
        assertEquals(0, decoded.bodyLength());
        assertTrue(decoded.headers().isEmpty());
        assertEquals(7L, decoded.completedAtEpochMilli());
    }

    /**
     * 损坏、空值和未知版本都返回 null，让调用方按"没有快照"走正常执行，而不是让接口 500。
     */
    @Test
    void shouldReturnNullForUnreadableInput() {
        assertNull(IdempotentRecordCodec.decode(null));
        assertNull(IdempotentRecordCodec.decode(""));
        assertNull(IdempotentRecordCodec.decode("1|200"));
        assertNull(IdempotentRecordCodec.decode("2|200|1|||"));
        assertNull(IdempotentRecordCodec.decode("1|abc|1|||"));
        assertNull(IdempotentRecordCodec.decode("1|200|1||!!!not-base64!!!|"));
    }

    /**
     * 响应体是可变数组，快照必须持有副本，否则调用方复用缓冲区会改掉已保存的内容。
     */
    @Test
    void shouldCopyBodyDefensively() {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        IdempotentRecord record = new IdempotentRecord(200, "text/plain", body, Map.of(), 1L);

        body[0] = 'X';

        assertEquals("ok", new String(record.body(), StandardCharsets.UTF_8));
    }
}
