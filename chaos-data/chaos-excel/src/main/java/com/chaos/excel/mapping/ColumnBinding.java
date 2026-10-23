package com.chaos.excel.mapping;

import java.lang.reflect.Field;

/**
 * 一列字段绑定：列索引、表头、模板元数据和反射字段。
 */
public final class ColumnBinding {

    private final int index;
    private final String name;
    private final boolean required;
    private final int width;
    private final String format;
    private final String dropdown;
    private final String hint;
    private final Field field;
    private final Class<? extends CellConverter<?>> converterType;

    ColumnBinding(
            int index,
            String name,
            boolean required,
            int width,
            String format,
            String dropdown,
            String hint,
            Field field,
            Class<? extends CellConverter<?>> converterType) {
        this.index = index;
        this.name = name;
        this.required = required;
        this.width = width;
        this.format = format == null ? "" : format;
        this.dropdown = dropdown == null ? "" : dropdown;
        this.hint = hint == null ? "" : hint;
        this.field = field;
        this.converterType = converterType;
    }

    /**
     * 用模板覆盖项生成新的列绑定。
     *
     * @param override 模板列定义
     * @return 应用覆盖项后的列绑定
     */
    public ColumnBinding withMetadata(ColumnSpecLike override) {
        return new ColumnBinding(
                index,
                override.name().isBlank() ? name : override.name(),
                override.required(),
                override.width() > 0 ? override.width() : width,
                override.format().isBlank() ? format : override.format(),
                override.dropdown().isBlank() ? dropdown : override.dropdown(),
                override.hint().isBlank() ? hint : override.hint(),
                field,
                converterType);
    }

    /** @return 从 0 开始的列索引 */
    public int index() {
        return index;
    }

    /** @return 表头名称 */
    public String name() {
        return name;
    }

    /** @return 是否必填 */
    public boolean required() {
        return required;
    }

    /** @return 模板列宽，0 表示默认 */
    public int width() {
        return width;
    }

    /** @return 显示格式 */
    public String format() {
        return format;
    }

    /** @return 下拉定义 key */
    public String dropdown() {
        return dropdown;
    }

    /** @return 填写提示 */
    public String hint() {
        return hint;
    }

    /** @return 反射字段 */
    public Field field() {
        return field;
    }

    /** @return 自定义转换器类型 */
    public Class<? extends CellConverter<?>> converterType() {
        return converterType;
    }

    /** 模板覆盖项的最小只读契约，避免 mapping 包依赖 template 包。 */
    public interface ColumnSpecLike {
        String name();

        boolean required();

        int width();

        String format();

        String dropdown();

        String hint();
    }
}
