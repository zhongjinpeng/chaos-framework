package com.chaos.excel;

import java.util.Objects;

/** 内存中的 XLSX 文档。 */
public record ExcelDocument(String filename, String contentType, byte[] content) {
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public ExcelDocument {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    public static ExcelDocument xlsx(String filename, byte[] content) {
        String normalized = filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                ? filename : filename + ".xlsx";
        return new ExcelDocument(normalized, XLSX_CONTENT_TYPE, content);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
