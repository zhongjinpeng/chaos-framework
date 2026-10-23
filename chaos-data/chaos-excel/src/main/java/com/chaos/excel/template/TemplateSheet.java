package com.chaos.excel.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一个模板数据 Sheet 的定义。
 */
public final class TemplateSheet {

    private final String sheetName;
    private final Class<?> rowType;
    private final List<?> samples;
    private final Map<String, List<String>> dropdowns;
    private final List<ColumnSpec> columns;
    private final String instructions;

    private TemplateSheet(Builder builder) {
        this.sheetName = builder.sheetName;
        this.rowType = builder.rowType;
        this.samples = List.copyOf(builder.samples);
        this.dropdowns = Collections.unmodifiableMap(new LinkedHashMap<>(builder.dropdowns));
        this.columns = List.copyOf(builder.columns);
        this.instructions = builder.instructions;
    }

    /**
     * 创建模板 Sheet 构建器。
     *
     * @param sheetName Sheet 名
     * @param rowType 行模型类型
     * @return 构建器
     */
    public static Builder builder(String sheetName, Class<?> rowType) {
        return new Builder(sheetName, rowType);
    }

    /** @return Sheet 名 */
    public String sheetName() {
        return sheetName;
    }

    /** @return 行模型类型 */
    public Class<?> rowType() {
        return rowType;
    }

    /** @return 示例数据 */
    public List<?> samples() {
        return samples;
    }

    /** @return 下拉定义 key 到选项的映射 */
    public Map<String, List<String>> dropdowns() {
        return dropdowns;
    }

    /** @return 编程式列覆盖项 */
    public List<ColumnSpec> columns() {
        return columns;
    }

    /** @return 该 Sheet 的填写说明 */
    public String instructions() {
        return instructions;
    }

    /** 模板 Sheet 构建器。 */
    public static final class Builder {

        private final String sheetName;
        private final Class<?> rowType;
        private final List<Object> samples = new ArrayList<>();
        private final Map<String, List<String>> dropdowns = new LinkedHashMap<>();
        private final List<ColumnSpec> columns = new ArrayList<>();
        private String instructions = "";

        private Builder(String sheetName, Class<?> rowType) {
            if (sheetName == null || sheetName.isBlank()) {
                throw new IllegalArgumentException("sheetName must not be blank");
            }
            this.sheetName = sheetName;
            this.rowType = Objects.requireNonNull(rowType, "rowType must not be null");
        }

        /**
         * 添加示例数据。
         *
         * @param values 示例行
         * @return 当前构建器
         */
        public Builder samples(Collection<?> values) {
            if (values != null) {
                samples.addAll(values);
            }
            return this;
        }

        /**
         * 添加下拉定义。
         *
         * @param key 定义 key
         * @param options 选项
         * @return 当前构建器
         */
        public Builder dropdown(String key, Collection<String> options) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("dropdown key must not be blank");
            }
            List<String> normalized = options == null
                    ? List.of()
                    : options.stream().filter(Objects::nonNull).map(String::strip)
                            .filter(value -> !value.isEmpty()).distinct().toList();
            dropdowns.put(key, normalized);
            return this;
        }

        /**
         * 添加列覆盖项。
         *
         * @param specs 列定义
         * @return 当前构建器
         */
        public Builder columns(Collection<ColumnSpec> specs) {
            if (specs != null) {
                columns.addAll(specs);
            }
            return this;
        }

        /**
         * 设置填写说明。
         *
         * @param value 说明文本
         * @return 当前构建器
         */
        public Builder instructions(String value) {
            this.instructions = value == null ? "" : value;
            return this;
        }

        /**
         * 构建模板 Sheet。
         *
         * @return 不可变定义
         */
        public TemplateSheet build() {
            return new TemplateSheet(this);
        }
    }
}
