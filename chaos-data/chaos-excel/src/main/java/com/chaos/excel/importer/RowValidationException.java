package com.chaos.excel.importer;

import com.chaos.excel.ExcelProcessingException;
import java.util.Objects;

/** 业务行校验失败。 */
public final class RowValidationException extends ExcelProcessingException {

    private final String code;
    private final String columnName;

    /**
     * 创建行校验异常。
     *
     * @param code 稳定错误码
     * @param columnName 关联列名，可为空
     * @param message 用户可读错误信息
     */
    public RowValidationException(String code, String columnName, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.columnName = columnName;
    }

    /** @return 稳定错误码 */
    public String code() {
        return code;
    }

    /** @return 关联列名，可能为空 */
    public String columnName() {
        return columnName;
    }
}
