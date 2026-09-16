package com.michael.chaos.storage;

import java.time.Instant;

/**
 * 对象存储文件元数据。
 *
 * @param bucket 存储桶名称
 * @param objectKey 对象 key
 * @param contentType 文件内容类型
 * @param size 文件大小，单位字节
 * @param createdAt 创建时间
 */
public record FileObject(
        String bucket,
        String objectKey,
        String contentType,
        long size,
        Instant createdAt
) {
}
