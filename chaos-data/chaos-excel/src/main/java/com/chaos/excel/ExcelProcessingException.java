package com.chaos.excel;

/** Excel 读取、转换或写出失败。 */
public class ExcelProcessingException extends RuntimeException {
    public ExcelProcessingException(String message) {
        super(message);
    }

    public ExcelProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
