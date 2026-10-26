package com.chaos.web.http;

import com.chaos.core.file.AttachmentFile;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    /**
     * 将应用层附件契约包装为附件下载响应。
     *
     * @param file 附件文件，不能为空
     * @return 带 UTF-8 文件名的附件响应
     */
    public static ResponseEntity<byte[]> of(AttachmentFile file) {
        Objects.requireNonNull(file, "file must not be null");
        return of(file.name(), file.contentType(), file.content());
    }

    /**
     * 将应用层附件契约包装为浏览器内嵌展示响应。
     *
     * @param file 附件文件，不能为空
     * @return 带内嵌文件名的响应
     */
    public static ResponseEntity<byte[]> inline(AttachmentFile file) {
        Objects.requireNonNull(file, "file must not be null");
        byte[] content = file.content();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(content.length)
                .body(content);
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

    /** 将文件内容生产过程交给 Servlet 容器分块写入附件响应。 */
    public static ResponseEntity<StreamingResponseBody> streaming(
            String fileName, String contentType, StreamingResponseBody body) {
        MediaType mediaType = MediaType.parseMediaType(
                Objects.requireNonNull(contentType, "contentType must not be null"));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(fileName))
                .body(Objects.requireNonNull(body, "body must not be null"));
    }

}
