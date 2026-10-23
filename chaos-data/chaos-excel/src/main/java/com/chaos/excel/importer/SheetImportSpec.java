package com.chaos.excel.importer;

import com.chaos.excel.mapping.RowMapper;
import java.util.Objects;

/**
 * 单个 Sheet 的导入定义。
 *
 * @param <T> 行模型类型
 */
public final class SheetImportSpec<T> {

    private final String sheetName;
    private final Class<T> rowType;
    private final int headerRows;
    private final boolean required;
    private final boolean requireData;
    private final int maxRows;
    private final int skipSampleRows;
    private final RowValidator<T> validator;
    private final RowHandler<T> handler;

    private SheetImportSpec(Builder<T> builder) {
        this.sheetName = builder.sheetName;
        this.rowType = builder.rowType;
        this.headerRows = builder.headerRows;
        this.required = builder.required;
        this.requireData = builder.requireData;
        this.maxRows = builder.maxRows;
        this.skipSampleRows = builder.skipSampleRows;
        this.validator = builder.validator;
        this.handler = builder.handler;
    }

    /**
     * 创建 Sheet 导入定义构建器。
     *
     * @param sheetName Sheet 名
     * @param rowType 行模型类型
     * @param <T> 行模型类型
     * @return 构建器
     */
    public static <T> Builder<T> builder(String sheetName, Class<T> rowType) {
        return new Builder<>(sheetName, rowType);
    }

    /** @return Sheet 名 */
    public String sheetName() {
        return sheetName;
    }

    /** @return 行模型类型 */
    public Class<T> rowType() {
        return rowType;
    }

    /** @return 表头行数 */
    public int headerRows() {
        return headerRows;
    }

    /** @return Sheet 缺失时是否失败 */
    public boolean required() {
        return required;
    }

    /** @return 没有数据行时是否失败 */
    public boolean requireData() {
        return requireData;
    }

    /** @return 最大数据行数 */
    public int maxRows() {
        return maxRows;
    }

    /** @return 跳过开头示例行数 */
    public int skipSampleRows() {
        return skipSampleRows;
    }

    /** @return 行校验器 */
    public RowValidator<T> validator() {
        return validator;
    }

    /** @return 行业务处理器 */
    public RowHandler<T> handler() {
        return handler;
    }

    /** Sheet 导入定义构建器。 */
    public static final class Builder<T> {

        private final String sheetName;
        private final Class<T> rowType;
        private int headerRows = 1;
        private boolean required = true;
        private boolean requireData = true;
        private int maxRows = 100_000;
        private int skipSampleRows;
        private RowValidator<T> validator = (row, rowNumber) -> {
        };
        private RowHandler<T> handler = (row, rowNumber) -> {
        };

        private Builder(String sheetName, Class<T> rowType) {
            if (sheetName == null || sheetName.isBlank()) {
                throw new IllegalArgumentException("sheetName must not be blank");
            }
            this.sheetName = sheetName;
            this.rowType = Objects.requireNonNull(rowType, "rowType must not be null");
        }

        /** @return 修改表头行数后的构建器 */
        public Builder<T> headerRows(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("headerRows must be at least 0");
            }
            this.headerRows = value;
            return this;
        }

        /** @return 修改 Sheet 必需性后的构建器 */
        public Builder<T> required(boolean value) {
            this.required = value;
            return this;
        }

        /** @return 修改空数据策略后的构建器 */
        public Builder<T> requireData(boolean value) {
            this.requireData = value;
            return this;
        }

        /** @return 修改最大行数后的构建器 */
        public Builder<T> maxRows(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxRows must be greater than 0");
            }
            this.maxRows = value;
            return this;
        }

        /** @return 修改跳过示例行数后的构建器 */
        public Builder<T> skipSampleRows(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("skipSampleRows must be at least 0");
            }
            this.skipSampleRows = value;
            return this;
        }

        /** @return 设置行校验器后的构建器 */
        public Builder<T> validator(RowValidator<T> value) {
            this.validator = Objects.requireNonNull(value, "validator must not be null");
            return this;
        }

        /** @return 设置行业务处理器后的构建器 */
        public Builder<T> handler(RowHandler<T> value) {
            this.handler = Objects.requireNonNull(value, "handler must not be null");
            return this;
        }

        /** @return 不可变 Sheet 导入定义 */
        public SheetImportSpec<T> build() {
            return new SheetImportSpec<>(this);
        }
    }
}
