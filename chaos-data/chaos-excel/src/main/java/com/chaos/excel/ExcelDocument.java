package com.chaos.excel;

import com.chaos.excel.internal.FileNames;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * 保存在内存中的 XLSX 文档。
 *
 * <p>文档内容采用防御性复制，适合模板和小型导出文件；大文件应优先使用
 * {@link StreamingExcelFile}。</p>
 */
public record ExcelDocument(String filename, String contentType, byte[] content) implements ExcelFile {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public ExcelDocument {
        filename = FileNames.xlsx(filename);
        if (!XLSX_CONTENT_TYPE.equals(contentType)) {
            throw new IllegalArgumentException("contentType must be the XLSX media type");
        }
        content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    /**
     * 创建 XLSX 内存文档。
     *
     * @param filename 文件名
     * @param content 文件字节
     * @return 内存文档
     */
    public static ExcelDocument xlsx(String filename, byte[] content) {
        return new ExcelDocument(filename, XLSX_CONTENT_TYPE, content);
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output must not be null").write(content);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
