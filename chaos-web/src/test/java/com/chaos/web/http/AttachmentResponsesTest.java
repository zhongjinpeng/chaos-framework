package com.chaos.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.chaos.core.file.AttachmentFile;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AttachmentResponsesTest {

    @Test
    void shouldBuildUtf8AttachmentResponse() {
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        var response = AttachmentResponses.of(
                "检测报告.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("filename*=UTF-8''");
        assertThat(response.getBody()).isSameAs(content);
    }

    @Test
    void shouldBuildInlineResponseFromAttachmentContract() {
        byte[] content = "preview".getBytes(StandardCharsets.UTF_8);
        AttachmentFile file = new AttachmentFile() {
            @Override
            public String name() {
                return "检测报告.pdf";
            }

            @Override
            public String contentType() {
                return MediaType.APPLICATION_PDF_VALUE;
            }

            @Override
            public byte[] content() {
                return content;
            }
        };

        var response = AttachmentResponses.inline(file);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline;")
                .contains("filename*=UTF-8''");
        assertThat(response.getBody()).isSameAs(content);
    }

    @Test
    void shouldRejectMissingContent() {
        assertThatNullPointerException()
                .isThrownBy(() -> AttachmentResponses.of(
                        "report.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, null))
                .withMessage("content must not be null");
    }

    @Test
    void shouldBuildStreamingAttachmentResponse() throws IOException {
        var response = AttachmentResponses.streaming(
                "检测报告.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                output -> output.write("content".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        response.getBody().writeTo(output);

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.getHeaders().getContentLength()).isEqualTo(-1);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("filename*=UTF-8''");
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("content");
    }

}
