package com.chaos.core.importing;

/**
 * 单行导入校验器。
 *
 * <p>校验失败必须抛出 {@link ImportRowValidationException}，框架会记录行号、列名、错误码和原始
 * 单元格内容后继续处理后续行；不得用返回值或日志代替失败信号。</p>
 *
 * @param <T> 应用层导入行类型
 */
@FunctionalInterface
public interface ImportRowValidator<T> {

    /**
     * 校验一行数据。
     *
     * @param row 应用层导入行
     * @param rowNumber 从 1 开始的 Excel 行号，用于错误定位
     */
    void validate(T row, int rowNumber);
}
