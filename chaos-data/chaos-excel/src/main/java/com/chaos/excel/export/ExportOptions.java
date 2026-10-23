package com.chaos.excel.export;

/**
 * 导出选项。
 *
 * @param includeHeader 是否写表头
 * @param freezeHeader 是否冻结首行
 * @param autoFilter 是否给表头加自动筛选
 * @param formulaSanitize 是否对以公式字符开头的文本做转义
 * @param rowAccessWindowSize SXSSF 内存窗口行数
 */
public record ExportOptions(
        boolean includeHeader,
        boolean freezeHeader,
        boolean autoFilter,
        boolean formulaSanitize,
        int rowAccessWindowSize) {

    public ExportOptions {
        if (rowAccessWindowSize < 1) {
            throw new IllegalArgumentException("rowAccessWindowSize must be greater than 0");
        }
    }

    /** @return 包含表头、冻结首行、不筛选、公式转义、100 行窗口的默认选项 */
    public static ExportOptions defaults() {
        return new ExportOptions(true, true, false, true, 100);
    }

    /** @return 修改表头开关后的选项 */
    public ExportOptions includeHeader(boolean value) {
        return new ExportOptions(value, freezeHeader, autoFilter, formulaSanitize, rowAccessWindowSize);
    }

    /** @return 修改冻结开关后的选项 */
    public ExportOptions freezeHeader(boolean value) {
        return new ExportOptions(includeHeader, value, autoFilter, formulaSanitize, rowAccessWindowSize);
    }

    /** @return 修改自动筛选开关后的选项 */
    public ExportOptions autoFilter(boolean value) {
        return new ExportOptions(includeHeader, freezeHeader, value, formulaSanitize, rowAccessWindowSize);
    }

    /** @return 修改公式转义开关后的选项 */
    public ExportOptions formulaSanitize(boolean value) {
        return new ExportOptions(includeHeader, freezeHeader, autoFilter, value, rowAccessWindowSize);
    }

    /** @return 修改 SXSSF 窗口后的选项 */
    public ExportOptions rowAccessWindowSize(int value) {
        return new ExportOptions(includeHeader, freezeHeader, autoFilter, formulaSanitize, value);
    }
}
