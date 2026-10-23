package com.chaos.excel.importer;

import java.util.Map;
import java.util.Objects;

/**
 * 一条行级导入错误。
 *
 * @param sheetName Sheet 名
 * @param rowNumber 从 1 开始的 Excel 行号
 * @param columnIndex 从 0 开始的列索引，行级错误为 -1
 * @param columnName 列名
 * @param code 稳定错误码
 * @param message 用户可读错误信息
 * @param rawCells 该行原始单元格文本，按列索引
 */
public record ImportError(
        String sheetName,
        int rowNumber,
        int columnIndex,
        String columnName,
        String code,
        String message,
        Map<Integer, String> rawCells) {

    public ImportError {
        Objects.requireNonNull(sheetName, "sheetName must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        rawCells = rawCells == null ? Map.of() : Map.copyOf(rawCells);
    }
}
