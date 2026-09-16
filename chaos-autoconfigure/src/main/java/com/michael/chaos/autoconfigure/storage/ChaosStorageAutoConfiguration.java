package com.michael.chaos.autoconfigure.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.michael.chaos.storage.ObjectStorageClient;
import com.michael.chaos.storage.PolicyEnforcingObjectStorageClient;
import com.michael.chaos.storage.StoragePolicy;
import com.michael.chaos.storage.minio.ChaosMinioProperties;
import com.michael.chaos.storage.minio.MinioObjectStorageClient;
import com.michael.chaos.storage.oss.ChaosOssProperties;
import com.michael.chaos.storage.oss.OssObjectStorageClient;
import io.minio.MinioClient;
import java.util.HashSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储 adapter 自动装配。
 *
 * <p>注册的 {@link ObjectStorageClient} 会包装 {@link PolicyEnforcingObjectStorageClient}，
 * 按 {@code chaos.storage.max-object-size}、{@code chaos.storage.allowed-content-types} 校验上传。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ChaosStorageProperties.class)
@ConditionalOnClass(name = "com.michael.chaos.storage.ObjectStorageClient")
public class ChaosStorageAutoConfiguration {

    /**
     * 根据配置构造上传策略。
     */
    static ObjectStorageClient withPolicy(ObjectStorageClient client, ChaosStorageProperties properties) {
        long maxSize = properties.getMaxObjectSize() == null ? 0 : properties.getMaxObjectSize().toBytes();
        StoragePolicy policy = new StoragePolicy(maxSize, new HashSet<>(properties.getAllowedContentTypes()));
        return new PolicyEnforcingObjectStorageClient(client, policy);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OSS.class)
    @ConditionalOnProperty(prefix = "chaos.storage", name = "provider", havingValue = "oss")
    @EnableConfigurationProperties(ChaosOssProperties.class)
    static class OssConfiguration {

        /**
         * 注册阿里云 OSS 客户端。
         */
        @Bean
        @ConditionalOnMissingBean
        public OSS ossClient(ChaosOssProperties properties) {
            return new OSSClientBuilder().build(
                    properties.getEndpoint(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret()
            );
        }

        /**
         * 注册阿里云 OSS 对象存储实现。
         */
        @Bean
        @ConditionalOnMissingBean(ObjectStorageClient.class)
        public ObjectStorageClient ossObjectStorageClient(OSS oss, ChaosStorageProperties storageProperties) {
            return withPolicy(new OssObjectStorageClient(oss), storageProperties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MinioClient.class)
    @ConditionalOnProperty(prefix = "chaos.storage", name = "provider", havingValue = "minio")
    @EnableConfigurationProperties(ChaosMinioProperties.class)
    static class MinioConfiguration {

        /**
         * 注册 MinIO 客户端。
         */
        @Bean
        @ConditionalOnMissingBean
        public MinioClient minioClient(ChaosMinioProperties properties) {
            return MinioClient.builder()
                    .endpoint(properties.getEndpoint())
                    .credentials(properties.getAccessKey(), properties.getSecretKey())
                    .build();
        }

        /**
         * 注册 MinIO 对象存储实现。
         */
        @Bean
        @ConditionalOnMissingBean(ObjectStorageClient.class)
        public ObjectStorageClient minioObjectStorageClient(MinioClient minioClient, ChaosStorageProperties storageProperties) {
            return withPolicy(new MinioObjectStorageClient(minioClient), storageProperties);
        }
    }
}
