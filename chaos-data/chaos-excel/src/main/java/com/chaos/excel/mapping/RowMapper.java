package com.chaos.excel.mapping;

import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.annotation.ExcelColumn;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 {@link ExcelColumn} 的行模型映射器。
 *
 * <p>一个映射器对应一个行类型，构造后被复用；映射过程不持有行状态，线程安全。</p>
 *
 * @param <T> 行模型类型
 */
public final class RowMapper<T> {

    private final Class<T> rowType;
    private final List<ColumnBinding> columns;
    private final Constructor<T> constructor;

    private RowMapper(Class<T> rowType, List<ColumnBinding> columns, Constructor<T> constructor) {
        this.rowType = rowType;
        this.columns = List.copyOf(columns);
        this.constructor = constructor;
    }

    /**
     * 创建行类型映射器。
     *
     * @param rowType 行模型类型，必须有可访问的无参构造器
     * @param <T> 行模型类型
     * @return 不可变映射器
     */
    public static <T> RowMapper<T> of(Class<T> rowType) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        List<Field> fields = annotatedFields(rowType);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Row type must declare at least one @ExcelColumn field: " + rowType.getName());
        }
        List<ColumnBinding> bindings = toBindings(fields);
        try {
            Constructor<T> constructor = rowType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return new RowMapper<>(rowType, bindings, constructor);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                    "Row type must declare a no-arg constructor: " + rowType.getName(), exception);
        }
    }

    private static List<Field> annotatedFields(Class<?> rowType) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = rowType; current != null && current != Object.class;
                current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())
                        && field.isAnnotationPresent(ExcelColumn.class)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static List<ColumnBinding> toBindings(List<Field> fields) {
        Set<Integer> usedIndexes = new HashSet<>();
        List<ColumnBinding> bindings = new ArrayList<>(fields.size());
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            String name = annotation.name() == null ? "" : annotation.name().strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "@ExcelColumn.name must not be blank: " + field.getName());
            }
            int index = annotation.index();
            if (index < 0) {
                index = nextFreeIndex(usedIndexes);
            } else if (!usedIndexes.add(index)) {
                throw new IllegalArgumentException(
                        "Duplicate @ExcelColumn index " + index + " on " + field.getName());
            }
            field.setAccessible(true);
            bindings.add(new ColumnBinding(
                    index,
                    name,
                    annotation.required(),
                    annotation.width(),
                    annotation.format(),
                    annotation.dropdown(),
                    annotation.hint(),
                    field,
                    annotation.converter()));
        }
        bindings.sort(Comparator.comparingInt(ColumnBinding::index));
        return bindings;
    }

    private static int nextFreeIndex(Set<Integer> usedIndexes) {
        int index = 0;
        while (usedIndexes.contains(index)) {
            index++;
        }
        usedIndexes.add(index);
        return index;
    }

    /**
     * 将一行单元格映射为行模型。
     *
     * @param cells 列索引到单元格值的映射，缺失列按空值处理
     * @param rowNumber 从 1 开始的 Excel 行号，用于错误定位
     * @return 行模型实例
     * @throws RowMappingException 必填、格式或公式缓存校验失败时抛出
     */
    public T map(Map<Integer, CellValue> cells, int rowNumber) {
        Objects.requireNonNull(cells, "cells must not be null");
        T row = newRow();
        for (ColumnBinding column : columns) {
            CellValue cell = cells.get(column.index());
            if (cell == null || cell.isBlank()) {
                if (column.required()) {
                    throw new RowMappingException(
                            RowMappingException.CODE_REQUIRED,
                            column.name(),
                            column.index(),
                            null,
                            "第 " + rowNumber + " 行必填列不能为空：" + column.name());
                }
                if (!column.field().getType().isPrimitive()) {
                    set(row, column, null);
                }
                continue;
            }
            Object value = ConverterRegistry.convert(cell, column.field().getType(), column);
            set(row, column, value);
        }
        return row;
    }

    private T newRow() {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new ExcelProcessingException(
                    "Cannot instantiate row type " + rowType.getName(), exception);
        }
    }

    private void set(T row, ColumnBinding column, Object value) {
        try {
            column.field().set(row, value);
        } catch (IllegalAccessException exception) {
            throw new ExcelProcessingException(
                    "Cannot write field " + column.field().getName(), exception);
        }
    }

    /**
     * 将行模型按列顺序转换为值列表，供导出使用。
     *
     * @param row 行模型
     * @return 与 {@link #columns()} 对齐的值列表
     */
    public List<Object> values(T row) {
        Objects.requireNonNull(row, "row must not be null");
        List<Object> values = new ArrayList<>(columns.size());
        for (ColumnBinding column : columns) {
            try {
                values.add(column.field().get(row));
            } catch (IllegalAccessException exception) {
                throw new ExcelProcessingException(
                        "Cannot read field " + column.field().getName(), exception);
            }
        }
        return values;
    }

    /** @return 按列索引排序的不可变列绑定 */
    public List<ColumnBinding> columns() {
        return columns;
    }

    /** @return 按列索引排序的不可变表头列表 */
    public List<String> headers() {
        return columns.stream().map(ColumnBinding::name).toList();
    }
}
