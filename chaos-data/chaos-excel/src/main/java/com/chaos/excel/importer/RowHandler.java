package com.chaos.excel.importer;

/**
 * 单行业务处理器。
 *
 * @param <T> 行模型类型
 */
@FunctionalInterface
public interface RowHandler<T> {

    /**
     * 处理一行已映射的数据。
     *
     * @param row 行模型
     * @param rowNumber 从 1 开始的 Excel 行号
     */
    void handle(T row, int rowNumber);
}
