package com.chaos.web.http;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * HTTP 附件下载响应构造工具，统一 UTF-8 文件名编码和内容元数据。
 */
public final class AttachmentResponses {

    private AttachmentResponses() {
    }

    /** 构造附件形式的 {@code Content-Disposition} 响应头。 */
    public static String contentDisposition(String fileName) {
        return ContentDisposition.attachment()
                .filename(
                        Objects.requireNonNull(fileName, "fileName must not be null"),
                        StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    /** 将内存中的文件内容包装为附件下载响应。 */
    public static ResponseEntity<byte[]> of(String fileName, String contentType, byte[] content) {
        byte[] body = Objects.requireNonNull(content, "content must not be null");
        MediaType mediaType = MediaType.parseMediaType(
                Objects.requireNonNull(contentType, "contentType must not be null"));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .body(body);
    }
}
