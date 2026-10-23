package com.chaos.excel.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次多 Sheet 导入的计划。
 */
public final class ImportPlan {

    private final List<SheetImportSpec<?>> sheets;
    private final ImportOptions options;
    private final ErrorPolicy errorPolicy;

    private ImportPlan(List<SheetImportSpec<?>> sheets, ImportOptions options, ErrorPolicy errorPolicy) {
        this.sheets = List.copyOf(sheets);
        this.options = options;
        this.errorPolicy = errorPolicy;
    }

    /** @return 导入计划构建器 */
    public static Builder builder() {
        return new Builder();
    }

    /** @return 按声明顺序排列的 Sheet 定义 */
    public List<SheetImportSpec<?>> sheets() {
        return sheets;
    }

    /** @return 导入边界 */
    public ImportOptions options() {
        return options;
    }

    /** @return 错误处理策略 */
    public ErrorPolicy errorPolicy() {
        return errorPolicy;
    }

    /** 导入计划构建器。 */
    public static final class Builder {

        private final List<SheetImportSpec<?>> sheets = new ArrayList<>();
        private ImportOptions options = ImportOptions.defaults();
        private ErrorPolicy errorPolicy = ErrorPolicy.collect(1_000);

        private Builder() {
        }

        /**
         * 添加一个 Sheet 定义。
         *
         * @param spec Sheet 定义
         * @return 当前构建器
         */
        public Builder sheet(SheetImportSpec<?> spec) {
            sheets.add(Objects.requireNonNull(spec, "spec must not be null"));
            return this;
        }

        /**
         * 设置导入边界。
         *
         * @param value 导入边界
         * @return 当前构建器
         */
        public Builder options(ImportOptions value) {
            this.options = Objects.requireNonNull(value, "options must not be null");
            return this;
        }

        /**
         * 设置错误策略。
         *
         * @param value 错误策略
         * @return 当前构建器
         */
        public Builder errorPolicy(ErrorPolicy value) {
            this.errorPolicy = Objects.requireNonNull(value, "errorPolicy must not be null");
            return this;
        }

        /**
         * 构建导入计划。
         *
         * @return 不可变计划
         */
        public ImportPlan build() {
            if (sheets.isEmpty()) {
                throw new IllegalArgumentException("Import plan must contain at least one sheet");
            }
            return new ImportPlan(sheets, options, errorPolicy);
        }
    }
}
