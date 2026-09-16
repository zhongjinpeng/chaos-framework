package com.michael.chaos.autoconfigure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.oss.OSS;
import com.michael.chaos.storage.ObjectStorageClient;
import com.michael.chaos.storage.PolicyEnforcingObjectStorageClient;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 对象存储自动装配测试。
 */
class ChaosStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosStorageAutoConfiguration.class));

    /**
     * 未显式选择 provider 时不应自动创建任何对象存储客户端。
     */
    @Test
    void shouldNotRegisterClientWhenProviderIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosStorageProperties.class);
            assertThat(context).doesNotHaveBean(ObjectStorageClient.class);
            assertThat(context).doesNotHaveBean(OSS.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
        });
    }

    /**
     * provider=minio 时只注册 MinIO 客户端和 ObjectStorageClient。
     */
    @Test
    void shouldRegisterMinioClientWhenProviderIsMinio() {
        contextRunner.withPropertyValues(
                        "chaos.storage.provider=minio",
                        "chaos.storage.minio.endpoint=http://localhost:9000",
                        "chaos.storage.minio.access-key=minio",
                        "chaos.storage.minio.secret-key=minio123"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MinioClient.class);
                    assertThat(context).hasSingleBean(ObjectStorageClient.class);
                    assertThat(context).doesNotHaveBean(OSS.class);
                });
    }

    /**
     * 配置了上传限制时应拒绝超限对象和非白名单类型。
     */
    @Test
    void shouldEnforceUploadPolicy() {
        contextRunner.withPropertyValues(
                        "chaos.storage.provider=minio",
                        "chaos.storage.minio.endpoint=http://localhost:9000",
                        "chaos.storage.minio.access-key=minio",
                        "chaos.storage.minio.secret-key=minio123",
                        "chaos.storage.max-object-size=1KB",
                        "chaos.storage.allowed-content-types=image/*"
                )
                .run(context -> {
                    ObjectStorageClient client = context.getBean(ObjectStorageClient.class);
                    assertThat(client).isInstanceOf(PolicyEnforcingObjectStorageClient.class);
                    assertThatThrownBy(() -> client.put("bucket", "a.txt", new ByteArrayInputStream(new byte[1]), 1, "text/plain"))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("contentType");
                    assertThatThrownBy(() -> client.put("bucket", "a.png", new ByteArrayInputStream(new byte[2048]), 2048, "image/png"))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("exceeds");
                });
    }

    /**
     * provider=oss 时只注册 OSS 客户端和 ObjectStorageClient。
     */
    @Test
    void shouldRegisterOssClientWhenProviderIsOss() {
        contextRunner.withPropertyValues(
                        "chaos.storage.provider=oss",
                        "chaos.storage.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                        "chaos.storage.oss.access-key-id=test-access-key",
                        "chaos.storage.oss.access-key-secret=test-access-secret"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OSS.class);
                    assertThat(context).hasSingleBean(ObjectStorageClient.class);
                    assertThat(context).doesNotHaveBean(MinioClient.class);
                });
    }
}
