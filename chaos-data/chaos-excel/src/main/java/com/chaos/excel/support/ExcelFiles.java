package com.chaos.excel.support;

import com.chaos.core.file.AttachmentFile;
import com.chaos.excel.ExcelFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * 把框架生成的 Excel 文件转换为业务层可下载附件。
 *
 * <p>框架文件采用写出式契约，业务侧统一以字节形式返回附件；本类集中完成内存缓冲和防御性复制。</p>
 */
public final class ExcelFiles {

    private ExcelFiles() {}

    /**
     * 把框架文件完整读入内存并转换为业务附件。
     *
     * @param file 框架生成的内存或流式 Excel 文件，不能为空
     * @return 携带文件名、媒体类型和完整字节的业务附件
     * @throws UncheckedIOException 框架写出文件失败时抛出
     */
    public static AttachmentFile toAttachment(ExcelFile file) {
        Objects.requireNonNull(file, "Excel 文件不能为空");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            file.writeTo(output);
        } catch (IOException exception) {
            throw new UncheckedIOException("Excel 文件写出失败", exception);
        }
        return new ByteArrayAttachmentFile(
                file.filename(), file.contentType(), output.toByteArray());
    }

    /**
     * 基于内存字节的附件实现，构造和读取都做防御性复制。
     *
     * @param name 下载文件名
     * @param contentType 文件媒体类型
     * @param content 文件完整二进制内容
     */
    private record ByteArrayAttachmentFile(String name, String contentType, byte[] content)
            implements AttachmentFile {

        private ByteArrayAttachmentFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
