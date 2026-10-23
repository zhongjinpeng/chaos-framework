package com.chaos.excel.mapping;

import com.chaos.excel.ExcelProcessingException;
import java.util.Objects;

/**
 * 单行映射失败，携带列名、列索引、原始值和稳定错误码。
 */
public final class RowMappingException extends ExcelProcessingException {

    public static final String CODE_REQUIRED = "EXCEL_REQUIRED";
    public static final String CODE_FORMAT = "EXCEL_FORMAT";
    public static final String CODE_FORMULA_NO_CACHE = "EXCEL_FORMULA_NO_CACHE";

    private final String code;
    private final String columnName;
    private final int columnIndex;
    private final String rawValue;

    /**
     * 创建不带底层异常的映射异常。
     *
     * @param code 稳定错误码
     * @param columnName 列名
     * @param columnIndex 从 0 开始的列索引
     * @param rawValue 原始单元格文本
     * @param message 用户可读错误信息
     */
    public RowMappingException(
            String code, String columnName, int columnIndex, String rawValue, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.rawValue = rawValue;
    }

    /**
     * 创建带底层异常的映射异常。
     *
     * @param code 稳定错误码
     * @param columnName 列名
     * @param columnIndex 从 0 开始的列索引
     * @param rawValue 原始单元格文本
     * @param message 用户可读错误信息
     * @param cause 底层异常
     */
    public RowMappingException(
            String code,
            String columnName,
            int columnIndex,
            String rawValue,
            String message,
            Throwable cause) {
        super(Objects.requireNonNull(message, "message must not be null"), cause);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.rawValue = rawValue;
    }

    /** @return 稳定错误码 */
    public String code() {
        return code;
    }

    /** @return 列名，可能为空 */
    public String columnName() {
        return columnName;
    }

    /** @return 从 0 开始的列索引，未知时为 -1 */
    public int columnIndex() {
        return columnIndex;
    }

    /** @return 原始单元格文本，可能为空 */
    public String rawValue() {
        return rawValue;
    }
}
