package com.michael.chaos.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.michael.chaos.storage.FileObject;
import com.michael.chaos.storage.ObjectStorageArguments;
import com.michael.chaos.storage.ObjectStorageClient;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 阿里云 OSS 对象存储客户端实现。
 */
public class OssObjectStorageClient implements ObjectStorageClient {

    private final OSS oss;

    /**
     * 创建 OSS 对象存储客户端。
     */
    public OssObjectStorageClient(OSS oss) {
        this.oss = oss;
    }

    /**
     * 上传对象到 OSS。
     */
    @Override
    public FileObject put(String bucket, String objectKey, InputStream inputStream, long size, String contentType) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        ObjectMetadata metadata = new ObjectMetadata();
        if (size >= 0) {
            // 大小未知时不设置 Content-Length，由 SDK 使用 chunked 上传；设置 -1 会导致请求失败。
            metadata.setContentLength(size);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        oss.putObject(bucket, objectKey, inputStream, metadata);
        return new FileObject(bucket, objectKey, contentType, Math.max(size, -1), Instant.now());
    }

    /**
     * 从 OSS 打开对象流。
     */
    @Override
    public InputStream get(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        return oss.getObject(bucket, objectKey).getObjectContent();
    }

    /**
     * 从 OSS 删除对象。
     */
    @Override
    public void delete(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        oss.deleteObject(bucket, objectKey);
    }

    /**
     * 创建 OSS 预签名 GET URL。
     */
    @Override
    public URL presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        ObjectStorageArguments.requirePresignedTtl(ttl);
        Date expiration = Date.from(Instant.now().plus(ttl));
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey);
        request.setExpiration(expiration);
        return oss.generatePresignedUrl(request);
    }
}
