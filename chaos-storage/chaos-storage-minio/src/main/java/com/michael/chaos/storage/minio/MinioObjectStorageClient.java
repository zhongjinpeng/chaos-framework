package com.michael.chaos.storage.minio;

import com.michael.chaos.storage.FileObject;
import com.michael.chaos.storage.ObjectStorageArguments;
import com.michael.chaos.storage.ObjectStorageClient;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;

/**
 * MinIO 对象存储客户端实现。
 */
public class MinioObjectStorageClient implements ObjectStorageClient {

    /**
     * 未知大小时的分块大小（10 MiB）。MinIO 要求 size 未知时必须指定 5 MiB ~ 5 GiB 的 partSize，
     * 旧实现固定传 -1，size 为 -1 时直接抛异常。
     */
    static final long UNKNOWN_SIZE_PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;

    /**
     * 创建 MinIO 对象存储客户端。
     */
    public MinioObjectStorageClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上传对象到 MinIO。
     */
    @Override
    public FileObject put(String bucket, String objectKey, InputStream inputStream, long size, String contentType) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        long objectSize = size < 0 ? -1 : size;
        long partSize = objectSize < 0 ? UNKNOWN_SIZE_PART_SIZE : -1;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, objectSize, partSize)
                    .contentType(contentType)
                    .build());
            return new FileObject(bucket, objectKey, contentType, objectSize, Instant.now());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO put object failed: " + objectKey, ex);
        }
    }

    /**
     * 从 MinIO 打开对象流。
     */
    @Override
    public InputStream get(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO get object failed: " + objectKey, ex);
        }
    }

    /**
     * 从 MinIO 删除对象。
     */
    @Override
    public void delete(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO delete object failed: " + objectKey, ex);
        }
    }

    /**
     * 创建 MinIO 预签名 GET URL。
     */
    @Override
    public URL presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        ObjectStorageArguments.requirePresignedTtl(ttl);
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds()))
                    .build());
            return new URL(url);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Invalid MinIO presigned URL: " + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO presigned URL failed: " + objectKey, ex);
        }
    }

    /**
     * 查询对象元数据。
     */
    public FileObject stat(String bucket, String objectKey) {
        ObjectStorageArguments.requireBucket(bucket);
        ObjectStorageArguments.requireObjectKey(objectKey);
        try {
            var stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new FileObject(bucket, objectKey, stat.contentType(), stat.size(), stat.lastModified().toInstant());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO stat object failed: " + objectKey, ex);
        }
    }
}
