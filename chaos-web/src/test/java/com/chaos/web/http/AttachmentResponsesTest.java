package com.chaos.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
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
    void shouldRejectMissingContent() {
        assertThatNullPointerException()
                .isThrownBy(() -> AttachmentResponses.of(
                        "report.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, null))
                .withMessage("content must not be null");
    }
}
