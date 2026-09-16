package com.michael.chaos.core.idempotent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link IdempotentRecord} 的文本编解码。
 *
 * <p>存在的理由：响应快照需要跨进程保存到 Redis 等集中式存储，而 chaos-core 不依赖任何序列化框架，
 * 也不应把格式交给各实现自行决定——否则滚动发布期间新旧实例写入的格式不一致，回放会直接失败。
 * 这里定义唯一的线格式，所有 {@link IdempotentRecordStore} 实现共用。</p>
 *
 * <p>格式（单行，字段以 {@code |} 分隔）：</p>
 * <pre>{@code
 * 1|<状态码>|<完成时间戳>|<base64 内容类型>|<base64 响应体>|<base64 头名>:<base64 头值>,...
 * }</pre>
 *
 * <p>自由文本一律使用无填充的 URL-safe Base64，字符集为 {@code A-Za-z0-9-_}，
 * 因此 {@code |}、{@code ,}、{@code :} 可以安全地作为分隔符。</p>
 */
public final class IdempotentRecordCodec {

    /**
     * 线格式版本号。解码时遇到未知版本返回 {@code null}，按"没有快照"处理，
     * 让请求走正常执行路径，而不是抛异常打断调用方。
     */
    private static final String VERSION = "1";

    private static final char FIELD_SEPARATOR = '|';

    private static final String HEADER_SEPARATOR = ",";

    private static final String HEADER_KEY_VALUE_SEPARATOR = ":";

    private static final int FIELD_COUNT = 6;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private IdempotentRecordCodec() {
    }

    /**
     * 编码响应快照。
     *
     * @param record 响应快照
     * @return 可直接写入字符串存储的编码结果
     */
    public static String encode(IdempotentRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        StringBuilder headers = new StringBuilder();
        record.headers().forEach((name, value) -> {
            if (headers.length() > 0) {
                headers.append(HEADER_SEPARATOR);
            }
            headers.append(encodeText(name)).append(HEADER_KEY_VALUE_SEPARATOR).append(encodeText(value));
        });
        return VERSION
                + FIELD_SEPARATOR + record.statusCode()
                + FIELD_SEPARATOR + record.completedAtEpochMilli()
                + FIELD_SEPARATOR + encodeText(record.contentType())
                + FIELD_SEPARATOR + ENCODER.encodeToString(record.body())
                + FIELD_SEPARATOR + headers;
    }

    /**
     * 解码响应快照。
     *
     * @param encoded 编码结果，可为 {@code null}
     * @return 解码成功返回快照；内容为空、版本不认识或格式损坏时返回 {@code null}
     */
    public static IdempotentRecord decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        String[] fields = splitFields(encoded);
        if (fields == null || !VERSION.equals(fields[0])) {
            return null;
        }
        try {
            int statusCode = Integer.parseInt(fields[1]);
            long completedAt = Long.parseLong(fields[2]);
            String contentType = decodeText(fields[3]);
            byte[] body = DECODER.decode(fields[4]);
            return new IdempotentRecord(statusCode, contentType, body, decodeHeaders(fields[5]), completedAt);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 按固定字段数切分，最后一个字段（响应头）允许为空字符串。
     *
     * <p>不能直接用 {@code String.split}：它会丢弃末尾空字段，没有响应头时会切出 5 段。</p>
     */
    private static String[] splitFields(String encoded) {
        String[] fields = new String[FIELD_COUNT];
        int from = 0;
        for (int i = 0; i < FIELD_COUNT - 1; i++) {
            int next = encoded.indexOf(FIELD_SEPARATOR, from);
            if (next < 0) {
                return null;
            }
            fields[i] = encoded.substring(from, next);
            from = next + 1;
        }
        fields[FIELD_COUNT - 1] = encoded.substring(from);
        return fields;
    }

    private static Map<String, String> decodeHeaders(String encoded) {
        if (encoded.isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String entry : encoded.split(HEADER_SEPARATOR)) {
            int separator = entry.indexOf(HEADER_KEY_VALUE_SEPARATOR);
            if (separator > 0) {
                String name = decodeText(entry.substring(0, separator));
                String value = decodeText(entry.substring(separator + 1));
                // 头值可以为空字符串，但不能为 null：回放时会直接写入响应头。
                headers.put(name, value == null ? "" : value);
            }
        }
        return headers;
    }

    /**
     * {@code null} 与空字符串都编码为空：空内容类型没有语义，解码时统一还原为 {@code null}。
     */
    private static String encodeText(String text) {
        return text == null || text.isEmpty() ? "" : ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encoded) {
        return encoded.isEmpty() ? null : new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
    }
}
