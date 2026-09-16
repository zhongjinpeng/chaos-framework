package com.michael.chaos.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * 应用服务使用的对象存储端口。
 *
 * <p>特定厂商实现位于 chaos-storage-oss、chaos-storage-minio 等适配器模块。实现必须通过
 * {@link ObjectStorageArguments} 校验 bucket、objectKey 和预签名 TTL。</p>
 */
public interface ObjectStorageClient {

    /**
     * 存储对象并返回对象元数据。
     *
     * <p>输入流由调用方负责关闭。</p>
     *
     * @param size 对象大小；未知时传 {@code -1}，实现会使用分块上传
     */
    FileObject put(String bucket, String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 打开对象输入流，调用方负责关闭。
     */
    InputStream get(String bucket, String objectKey);

    /**
     * 删除对象。
     */
    void delete(String bucket, String objectKey);

    /**
     * 创建临时 GET 访问 URL。
     *
     * @param ttl 有效期，必须在 1 秒到 7 天之间
     */
    URL presignedGetUrl(String bucket, String objectKey, Duration ttl);
}
