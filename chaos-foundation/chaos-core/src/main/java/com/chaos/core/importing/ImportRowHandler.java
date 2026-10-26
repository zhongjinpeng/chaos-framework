package com.chaos.core.importing;

/**
 * 单行导入数据处理器。
 *
 * <p>框架按文件顺序逐行回调；实现内部抛出的 {@link ImportRowValidationException} 会被收集为行级
 * 错误并继续处理后续行，其他运行时异常视为整份文件不可解析。</p>
 *
 * @param <T> 应用层导入行类型
 */
@FunctionalInterface
public interface ImportRowHandler<T> {

    /**
     * 处理一行已解析的数据。
     *
     * @param row 应用层导入行
     * @param rowNumber 从 1 开始的 Excel 行号，用于错误定位
     */
    void handle(T row, int rowNumber);
}
