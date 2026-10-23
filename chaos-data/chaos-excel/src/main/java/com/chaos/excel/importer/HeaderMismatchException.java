package com.chaos.excel.importer;

import com.chaos.excel.ExcelProcessingException;
import java.util.List;

/** Excel 表头与行模型定义不一致。 */
public final class HeaderMismatchException extends ExcelProcessingException {

    private final List<String> expected;
    private final List<String> actual;

    /**
     * 创建表头不匹配异常。
     *
     * @param expected 期望表头
     * @param actual 实际表头
     */
    public HeaderMismatchException(List<String> expected, List<String> actual) {
        super("Excel header mismatch; expected " + expected + " but was " + actual);
        this.expected = List.copyOf(expected);
        this.actual = List.copyOf(actual);
    }

    /** @return 期望表头 */
    public List<String> expected() {
        return expected;
    }

    /** @return 实际表头 */
    public List<String> actual() {
        return actual;
    }
}
