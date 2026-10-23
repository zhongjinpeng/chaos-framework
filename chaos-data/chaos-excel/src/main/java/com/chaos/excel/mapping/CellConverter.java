package com.chaos.excel.mapping;

/**
 * 单个字段的单元格转换扩展点。
 *
 * @param <T> 目标字段类型
 */
public interface CellConverter<T> {

    /**
     * 将单元格值转换为字段值。
     *
     * @param cell 单元格值
     * @param column 列绑定
     * @return 字段值
     */
    T read(CellValue cell, ColumnBinding column);

    /**
     * 将字段值转换为导出单元格值；默认直接返回原值。
     *
     * @param value 字段值
     * @param column 列绑定
     * @return 可写入 POI 的值
     */
    default Object write(T value, ColumnBinding column) {
        return value;
    }
}
