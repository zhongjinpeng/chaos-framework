package com.michael.chaos.storage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 对象存储参数校验。
 *
 * <p>所有 {@link ObjectStorageClient} 实现在调用厂商 SDK 前统一校验，避免：</p>
 * <ul>
 *     <li>业务把用户上传的文件名拼进 objectKey 时，通过 {@code ../}、开头的 {@code /} 或反斜杠覆盖其他用户或租户的对象；</li>
 *     <li>预签名 TTL 为 {@code null} 时 NPE，或超过 S3/MinIO 上限 7 天时在运行期才报错。</li>
 * </ul>
 */
public final class ObjectStorageArguments {

    /**
     * 预签名 URL 最短有效期。
     */
    public static final Duration MIN_PRESIGNED_TTL = Duration.ofSeconds(1);

    /**
     * 预签名 URL 最长有效期，S3 签名 V4 与 MinIO 的上限均为 7 天。
     */
    public static final Duration MAX_PRESIGNED_TTL = Duration.ofDays(7);

    /**
     * objectKey 最大 UTF-8 字节数（S3/OSS 上限 1024）。
     */
    public static final int MAX_OBJECT_KEY_BYTES = 1024;

    private ObjectStorageArguments() {
    }

    /**
     * 校验 bucket 名称。
     */
    public static String requireBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        return bucket;
    }

    /**
     * 校验对象 key。
     *
     * @throws IllegalArgumentException key 为空、过长、以 / 开头、包含反斜杠或控制字符、包含 . 或 .. 路径段
     */
    public static String requireObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (objectKey.getBytes(StandardCharsets.UTF_8).length > MAX_OBJECT_KEY_BYTES) {
            throw new IllegalArgumentException("objectKey must not exceed " + MAX_OBJECT_KEY_BYTES + " bytes");
        }
        if (objectKey.startsWith("/")) {
            throw new IllegalArgumentException("objectKey must not start with '/'");
        }
        for (int i = 0; i < objectKey.length(); i++) {
            char ch = objectKey.charAt(i);
            if (ch == '\\') {
                throw new IllegalArgumentException("objectKey must not contain '\\'");
            }
            if (ch < 0x20 || ch == 0x7f) {
                throw new IllegalArgumentException("objectKey must not contain control characters");
            }
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("objectKey must not contain '.' or '..' path segments");
            }
        }
        return objectKey;
    }

    /**
     * 校验预签名 URL 有效期。
     *
     * @throws IllegalArgumentException TTL 为空或不在 1 秒到 7 天之间
     */
    public static Duration requirePresignedTtl(Duration ttl) {
        if (ttl == null || ttl.compareTo(MIN_PRESIGNED_TTL) < 0 || ttl.compareTo(MAX_PRESIGNED_TTL) > 0) {
            throw new IllegalArgumentException("presigned url ttl must be between 1s and 7d");
        }
        return ttl;
    }
}
