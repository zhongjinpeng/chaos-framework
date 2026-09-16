package com.michael.chaos.core.idempotent;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 幂等请求首次执行成功后保留的响应快照。
 *
 * <p>只做"拒绝重复"时，客户端网络超时重发会拿到 409，但它并不知道第一次到底成功没有，只能再查一次单据状态。
 * 保留响应快照后，同一个幂等 key 的重复请求可以直接回放首次响应，客户端行为与第一次完全一致。</p>
 *
 * <p>只保留状态码、内容类型、少量响应头和响应体：这是复现一次 JSON API 响应所需的最小集合。
 * 响应头默认只保留 {@code Location}（创建类接口的资源地址），其余头由回放方重新生成。</p>
 */
public final class IdempotentRecord {

    private final int statusCode;

    private final String contentType;

    private final byte[] body;

    private final Map<String, String> headers;

    private final long completedAtEpochMilli;

    /**
     * 创建响应快照。
     *
     * @param statusCode 首次响应的 HTTP 状态码
     * @param contentType 首次响应的内容类型，可为 {@code null}
     * @param body 首次响应体，{@code null} 视为空响应体
     * @param headers 需要一并回放的响应头，{@code null} 视为空
     * @param completedAtEpochMilli 首次执行完成时间戳，单位毫秒
     */
    public IdempotentRecord(
            int statusCode,
            String contentType,
            byte[] body,
            Map<String, String> headers,
            long completedAtEpochMilli) {
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body.clone();
        this.headers = headers == null || headers.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.completedAtEpochMilli = completedAtEpochMilli;
    }

    /**
     * 返回首次响应的 HTTP 状态码。
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 返回首次响应的内容类型，可能为 {@code null}。
     */
    public String contentType() {
        return contentType;
    }

    /**
     * 返回首次响应体的副本。
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * 返回响应体字节数，避免调用方为了取长度而复制整个数组。
     */
    public int bodyLength() {
        return body.length;
    }

    /**
     * 返回需要一并回放的响应头（不可修改）。
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 返回首次执行完成时间戳，单位毫秒。
     */
    public long completedAtEpochMilli() {
        return completedAtEpochMilli;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IdempotentRecord other)) {
            return false;
        }
        return statusCode == other.statusCode
                && completedAtEpochMilli == other.completedAtEpochMilli
                && Objects.equals(contentType, other.contentType)
                && Arrays.equals(body, other.body)
                && headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, contentType, Arrays.hashCode(body), headers, completedAtEpochMilli);
    }

    @Override
    public String toString() {
        return "IdempotentRecord{statusCode=" + statusCode
                + ", contentType=" + contentType
                + ", bodyLength=" + body.length
                + ", headers=" + headers.keySet()
                + ", completedAtEpochMilli=" + completedAtEpochMilli
                + "}";
    }
}
