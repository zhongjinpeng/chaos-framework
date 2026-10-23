package com.chaos.excel.mapping;

/**
 * 自动转换哨兵。
 *
 * <p>该类型只用于 {@code @ExcelColumn.converter()} 的默认值，实际转换由
 * {@link ConverterRegistry} 按字段类型完成。</p>
 */
public final class AutoCellConverter implements CellConverter<Object> {

    @Override
    public Object read(CellValue cell, ColumnBinding column) {
        throw new IllegalStateException("AutoCellConverter must be resolved by ConverterRegistry");
    }
}
