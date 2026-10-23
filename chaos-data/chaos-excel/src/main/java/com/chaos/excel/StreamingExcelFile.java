package com.chaos.excel;

import com.chaos.excel.internal.FileNames;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * 延迟、流式的 XLSX 导出文件。
 *
 * <p>数据批次只会在调用 {@link #writeTo(OutputStream)} 时产生，适合大文件响应下载。</p>
 */
public final class StreamingExcelFile implements ExcelFile {

    private final String filename;
    private final Writer writer;

    StreamingExcelFile(String filename, Writer writer) {
        this.filename = FileNames.xlsx(filename);
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    @Override
    public String filename() {
        return filename;
    }

    @Override
    public String contentType() {
        return ExcelDocument.XLSX_CONTENT_TYPE;
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        writer.write(Objects.requireNonNull(output, "output must not be null"));
    }

    /** 输出流写出函数。 */
    @FunctionalInterface
    interface Writer {
        void write(OutputStream output) throws IOException;
    }
}
