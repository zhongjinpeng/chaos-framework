package com.michael.chaos.storage.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * MinIO 客户端参数处理测试。
 */
class MinioObjectStorageClientTest {

    private final MinioClient minioClient = mock(MinioClient.class);

    /**
     * 大小未知时必须指定分块大小，否则 MinIO SDK 直接报错。
     */
    @Test
    void shouldUsePartSizeWhenSizeUnknown() throws Exception {
        new MinioObjectStorageClient(minioClient).put("bucket", "a.txt", new ByteArrayInputStream(new byte[3]), -1, "text/plain");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().partSize()).isEqualTo(MinioObjectStorageClient.UNKNOWN_SIZE_PART_SIZE);
        assertThat(captor.getValue().objectSize()).isEqualTo(-1);
    }

    /**
     * 非法 key 和 TTL 在调用 SDK 前拒绝。
     */
    @Test
    void shouldValidateArgumentsBeforeCallingSdk() {
        MinioObjectStorageClient client = new MinioObjectStorageClient(minioClient);

        assertThatThrownBy(() -> client.put("bucket", "../x", new ByteArrayInputStream(new byte[1]), 1, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.presignedGetUrl("bucket", "a.txt", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.presignedGetUrl("bucket", "a.txt", Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(minioClient);
    }
}
