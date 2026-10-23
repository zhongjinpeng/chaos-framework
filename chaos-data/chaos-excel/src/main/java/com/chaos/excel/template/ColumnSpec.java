package com.chaos.excel.template;

import com.chaos.excel.mapping.ColumnBinding;

/**
 * 模板列定义的编程式覆盖项。
 *
 * @param index 从 0 开始的列索引
 * @param name 表头名称
 * @param required 是否必填
 * @param width 列宽，0 表示默认
 * @param format 显示格式
 * @param dropdown 下拉定义 key
 * @param hint 填写提示
 */
public record ColumnSpec(
        int index,
        String name,
        boolean required,
        int width,
        String format,
        String dropdown,
        String hint) implements ColumnBinding.ColumnSpecLike {

    public ColumnSpec {
        if (index < 0) {
            throw new IllegalArgumentException("index must be at least 0");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (width < 0) {
            throw new IllegalArgumentException("width must be at least 0");
        }
        format = format == null ? "" : format;
        dropdown = dropdown == null ? "" : dropdown;
        hint = hint == null ? "" : hint;
    }

    /**
     * 创建列定义。
     *
     * @param index 列索引
     * @param name 表头
     * @return 列定义
     */
    public static ColumnSpec of(int index, String name) {
        return new ColumnSpec(index, name, false, 0, "", "", "");
    }

    /** @return 修改列宽后的定义 */
    public ColumnSpec width(int value) {
        return new ColumnSpec(index, name, required, value, format, dropdown, hint);
    }

    /** @return 修改必填标记后的定义 */
    public ColumnSpec required(boolean value) {
        return new ColumnSpec(index, name, value, width, format, dropdown, hint);
    }

    /** @return 修改显示格式后的定义 */
    public ColumnSpec format(String value) {
        return new ColumnSpec(index, name, required, width, value, dropdown, hint);
    }

    /** @return 修改下拉 key 后的定义 */
    public ColumnSpec dropdown(String value) {
        return new ColumnSpec(index, name, required, width, format, value, hint);
    }

    /** @return 修改提示后的定义 */
    public ColumnSpec hint(String value) {
        return new ColumnSpec(index, name, required, width, format, dropdown, value);
    }
}
