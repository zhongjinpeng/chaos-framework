package com.chaos.web.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.file.AttachmentFile;
import com.chaos.core.importing.ImportOutcome;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * 校验导入接口“错误文件或成功行数”的统一响应约定。
 */
class ImportFileResponsesTest {

    @Test
    void successOutcomeShouldReturnSuccessRows() {
        var response = ImportFileResponses.from(new ImportOutcome(3L, 3L, 0L, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(3L);
    }

    @Test
    void outcomeWithErrorFileShouldReturnAttachment() {
        byte[] content = "error".getBytes(StandardCharsets.UTF_8);
        AttachmentFile errorFile =
                new TestAttachment("错误数据.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);

        var response = ImportFileResponses.from(new ImportOutcome(3L, 0L, 1L, errorFile));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''");
        assertThat(response.getBody()).isSameAs(content);
    }

    private record TestAttachment(String name, String contentType, byte[] content)
            implements AttachmentFile {}
}
