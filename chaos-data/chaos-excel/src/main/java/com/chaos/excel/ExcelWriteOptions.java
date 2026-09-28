package com.chaos.excel;

/** Excel 导出选项。 */
public record ExcelWriteOptions(String sheetName, boolean includeHeader) {
    public ExcelWriteOptions {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
        if (sheetName.length() > 31) {
            throw new IllegalArgumentException("sheetName must not exceed 31 characters");
        }
    }

    public static ExcelWriteOptions defaults() {
        return new ExcelWriteOptions("Sheet1", true);
    }
}
