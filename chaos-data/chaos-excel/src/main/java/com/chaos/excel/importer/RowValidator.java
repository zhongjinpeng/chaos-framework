package com.chaos.excel.importer;

/**
 * 单行业务校验器。
 *
 * @param <T> 行模型类型
 */
@FunctionalInterface
public interface RowValidator<T> {

    /**
     * 校验一行数据，失败时抛出 {@link RowValidationException}。
     *
     * @param row 行模型
     * @param rowNumber 从 1 开始的 Excel 行号
     */
    void validate(T row, int rowNumber);
}
