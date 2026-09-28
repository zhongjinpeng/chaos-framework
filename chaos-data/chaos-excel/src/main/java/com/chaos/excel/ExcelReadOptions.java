package com.chaos.excel;

/** Excel 导入选项。 */
public record ExcelReadOptions(int sheetNumber, int headerRows, int batchSize, int maxRows) {
    public static final int DEFAULT_BATCH_SIZE = 500;
    public static final int DEFAULT_MAX_ROWS = 100_000;

    public ExcelReadOptions {
        if (sheetNumber < 0) {
            throw new IllegalArgumentException("sheetNumber must be at least 0");
        }
        if (headerRows < 0) {
            throw new IllegalArgumentException("headerRows must be at least 0");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be greater than 0");
        }
    }

    public static ExcelReadOptions defaults() {
        return new ExcelReadOptions(0, 1, DEFAULT_BATCH_SIZE, DEFAULT_MAX_ROWS);
    }
}
