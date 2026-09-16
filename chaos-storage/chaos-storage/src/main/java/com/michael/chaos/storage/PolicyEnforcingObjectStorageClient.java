package com.michael.chaos.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Objects;

/**
 * 按 {@link StoragePolicy} 校验上传的对象存储装饰器。
 *
 * <p>大小已知时直接比较；大小未知（{@code size < 0}）时包装输入流，读取超过上限立即中断上传，
 * 防止客户端通过分块上传绕过大小限制。</p>
 */
public class PolicyEnforcingObjectStorageClient implements ObjectStorageClient {

    private final ObjectStorageClient delegate;

    private final StoragePolicy policy;

    /**
     * 创建策略装饰器。
     */
    public PolicyEnforcingObjectStorageClient(ObjectStorageClient delegate, StoragePolicy policy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.policy = policy == null ? StoragePolicy.unrestricted() : policy;
    }

    /**
     * 校验 contentType 和大小后上传。
     */
    @Override
    public FileObject put(String bucket, String objectKey, InputStream inputStream, long size, String contentType) {
        if (!policy.allowsContentType(contentType)) {
            throw new IllegalArgumentException("contentType is not allowed: " + contentType);
        }
        if (policy.limitsSize() && size > policy.maxObjectSize()) {
            throw new IllegalArgumentException("object size exceeds limit " + policy.maxObjectSize() + " bytes");
        }
        InputStream stream = policy.limitsSize() && size < 0
                ? new LimitedInputStream(inputStream, policy.maxObjectSize())
                : inputStream;
        return delegate.put(bucket, objectKey, stream, size, contentType);
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        return delegate.get(bucket, objectKey);
    }

    @Override
    public void delete(String bucket, String objectKey) {
        delegate.delete(bucket, objectKey);
    }

    @Override
    public URL presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        return delegate.presignedGetUrl(bucket, objectKey, ttl);
    }

    /**
     * 读取超过上限时抛出 IOException 的输入流。
     */
    static final class LimitedInputStream extends FilterInputStream {

        private final long limit;

        private long read;

        LimitedInputStream(InputStream in, long limit) {
            super(Objects.requireNonNull(in, "inputStream must not be null"));
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) throws IOException {
            read += n;
            if (read > limit) {
                throw new IOException("object size exceeds limit " + limit + " bytes");
            }
        }
    }
}
