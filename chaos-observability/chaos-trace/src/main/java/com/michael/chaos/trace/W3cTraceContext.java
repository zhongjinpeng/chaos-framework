package com.michael.chaos.trace;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * W3C Trace Context 的不可变模型。
 *
 * <p>该类只处理 {@code traceparent} 的协议解析与生成，不依赖 OpenTelemetry SDK，
 * 用于保持 Web、Gateway、Feign 和 MQ 的跨进程透传格式一致。</p>
 *
 * @param traceId 32 位小写十六进制 trace id
 * @param spanId 16 位小写十六进制 span id
 * @param traceFlags 2 位十六进制 trace flags
 */
public record W3cTraceContext(
        String traceId,
        String spanId,
        String traceFlags
) {

    /**
     * W3C Trace Context 当前稳定版本。
     */
    public static final String VERSION = "00";

    /**
     * 默认采样标记，表示当前链路允许采样。
     */
    public static final String SAMPLED_FLAGS = "01";

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("^[0-9a-f]{16}$");

    private static final Pattern TRACE_FLAGS_PATTERN = Pattern.compile("^[0-9a-f]{2}$");

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9a-f]{2}$");

    /**
     * W3C 规范保留的非法版本号。
     */
    private static final String INVALID_VERSION = "ff";

    /**
     * traceparent 长度上限，防止超长请求头消耗解析资源。
     */
    private static final int MAX_TRACEPARENT_LENGTH = 512;

    /**
     * 规范化并校验 W3C trace 字段。
     */
    public W3cTraceContext {
        traceId = normalize(traceId);
        spanId = normalize(spanId);
        traceFlags = normalize(traceFlags);
        if (!isValidTraceId(traceId)) {
            throw new IllegalArgumentException("traceId must be 32 lowercase hex characters and not all zeros");
        }
        if (!isValidSpanId(spanId)) {
            throw new IllegalArgumentException("spanId must be 16 lowercase hex characters and not all zeros");
        }
        if (!TRACE_FLAGS_PATTERN.matcher(traceFlags).matches()) {
            throw new IllegalArgumentException("traceFlags must be 2 lowercase hex characters");
        }
    }

    /**
     * 从 traceId 和 spanId 创建 W3C 上下文。
     */
    public static Optional<W3cTraceContext> from(String traceId, String spanId) {
        return from(traceId, spanId, SAMPLED_FLAGS);
    }

    /**
     * 从 traceId、spanId 和 trace flags 创建 W3C 上下文。
     */
    public static Optional<W3cTraceContext> from(String traceId, String spanId, String traceFlags) {
        String normalizedTraceId = normalize(traceId);
        String normalizedSpanId = normalize(spanId);
        String normalizedTraceFlags = normalize(traceFlags);
        if (!isValidTraceId(normalizedTraceId)
                || !isValidSpanId(normalizedSpanId)
                || !TRACE_FLAGS_PATTERN.matcher(normalizedTraceFlags).matches()) {
            return Optional.empty();
        }
        return Optional.of(new W3cTraceContext(normalizedTraceId, normalizedSpanId, normalizedTraceFlags));
    }

    /**
     * 解析 traceparent 请求头。
     */
    public static Optional<W3cTraceContext> parse(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Optional.empty();
        }
        String normalized = traceparent.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_TRACEPARENT_LENGTH) {
            return Optional.empty();
        }
        String[] parts = normalized.split("-", -1);
        if (parts.length < 4 || !VERSION_PATTERN.matcher(parts[0]).matches() || INVALID_VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        if (VERSION.equals(parts[0])) {
            // 版本 00 必须严格为 4 段。
            if (parts.length != 4) {
                return Optional.empty();
            }
        }
        // W3C 规范要求：遇到更高版本时按 00 版本解析前 4 段，后续新增字段直接忽略，保证前向兼容。
        return from(parts[1], parts[2], parts[3]);
    }

    /**
     * 生成 traceparent 请求头值。
     */
    public String traceparent() {
        return VERSION + "-" + traceId + "-" + spanId + "-" + traceFlags;
    }

    /**
     * 判断 traceId 是否符合 W3C 规范。
     */
    public static boolean isValidTraceId(String traceId) {
        return TRACE_ID_PATTERN.matcher(normalize(traceId)).matches() && !isAllZeros(traceId);
    }

    /**
     * 判断 spanId 是否符合 W3C 规范。
     */
    public static boolean isValidSpanId(String spanId) {
        return SPAN_ID_PATTERN.matcher(normalize(spanId)).matches() && !isAllZeros(spanId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isAllZeros(String value) {
        String normalized = normalize(value);
        return !normalized.isBlank() && normalized.chars().allMatch(ch -> ch == '0');
    }
}
