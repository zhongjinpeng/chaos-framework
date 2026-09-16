package com.michael.chaos.storage.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * OSS 客户端参数处理测试。
 */
class OssObjectStorageClientTest {

    private final OSS oss = mock(OSS.class);

    /**
     * 大小未知时不应设置 Content-Length。
     */
    @Test
    void shouldNotSetContentLengthWhenSizeUnknown() {
        new OssObjectStorageClient(oss).put("bucket", "a.txt", new ByteArrayInputStream(new byte[3]), -1, "text/plain");

        ArgumentCaptor<ObjectMetadata> captor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(oss).putObject(eq("bucket"), eq("a.txt"), any(InputStream.class), captor.capture());
        assertThat(captor.getValue().getRawMetadata()).doesNotContainKey("Content-Length");
    }

    /**
     * 非法 key 和 TTL 在调用 SDK 前拒绝。
     */
    @Test
    void shouldValidateArgumentsBeforeCallingSdk() {
        OssObjectStorageClient client = new OssObjectStorageClient(oss);

        assertThatThrownBy(() -> client.delete("bucket", "/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.presignedGetUrl("bucket", "a.txt", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(oss);
    }
}
