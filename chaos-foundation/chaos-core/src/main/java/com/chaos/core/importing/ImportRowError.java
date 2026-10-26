package com.chaos.core.importing;

import java.util.Map;
import java.util.Objects;

/**
 * 一条行级导入错误，应用层与文件解析实现共用。
 *
 * <p>文件解析实现负责填充工作表、原始单元格等定位信息；应用层手工构造行级错误时可以使用
 * 五参数构造器，此时工作表名称为空、原始单元格为空映射。</p>
 *
 * @param sheetName 工作表名称；应用层手工构造时可为 {@code null}
 * @param rowNumber 从 1 开始的 Excel 行号
 * @param columnIndex 从 0 开始的列索引，整行错误时为 -1
 * @param columnName 列名，整行错误时为空
 * @param code 稳定错误码，区分格式错误与业务错误
 * @param message 面向用户的错误原因
 * @param rawCells 该行原始单元格文本，按列索引；没有原始数据时为空映射
 */
public record ImportRowError(
        String sheetName,
        int rowNumber,
        int columnIndex,
        String columnName,
        String code,
        String message,
        Map<Integer, String> rawCells) {

    /**
     * 固化原始单元格并校验必要字段。
     */
    public ImportRowError {
        code = Objects.requireNonNull(code, "code must not be null");
        message = Objects.requireNonNull(message, "message must not be null");
        rawCells = rawCells == null ? Map.of() : Map.copyOf(rawCells);
    }

    /**
     * 创建不包含工作表和原始单元格信息的应用层行级错误。
     *
     * @param rowNumber 从 1 开始的 Excel 行号
     * @param columnIndex 从 0 开始的列索引，整行错误时为 -1
     * @param columnName 列名，整行错误时为空
     * @param code 稳定错误码
     * @param message 面向用户的错误原因
     */
    public ImportRowError(
            int rowNumber, int columnIndex, String columnName, String code, String message) {
        this(null, rowNumber, columnIndex, columnName, code, message, Map.of());
    }
}
