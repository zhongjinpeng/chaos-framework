package com.chaos.excel;

/** Excel 数据行数超过读取配置上限。 */
public class RowLimitExceededException extends ExcelProcessingException {

    private final int maxRows;

    /**
     * 创建携带本次读取上限的异常。
     *
     * @param maxRows 本次读取允许的最大数据行数
     */
    public RowLimitExceededException(int maxRows) {
        super("Excel row limit exceeded: " + maxRows);
        this.maxRows = maxRows;
    }

    /** @return 本次读取允许的最大数据行数 */
    public int maxRows() {
        return maxRows;
    }
}
