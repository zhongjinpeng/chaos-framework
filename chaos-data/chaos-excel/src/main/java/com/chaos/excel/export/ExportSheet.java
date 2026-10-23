package com.chaos.excel.export;

import java.util.Collection;
import java.util.Objects;

/**
 * 一个待导出的 Sheet。
 */
public final class ExportSheet {

    private final String sheetName;
    private final Class<?> rowType;
    private final Iterable<? extends Collection<?>> batches;

    private ExportSheet(
            String sheetName, Class<?> rowType, Iterable<? extends Collection<?>> batches) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
        if (sheetName.length() > 31) {
            throw new IllegalArgumentException("sheetName must not exceed 31 characters");
        }
        this.sheetName = sheetName;
        this.rowType = Objects.requireNonNull(rowType, "rowType must not be null");
        this.batches = Objects.requireNonNull(batches, "batches must not be null");
    }

    /**
     * 创建一个 Sheet 导出定义。
     *
     * @param sheetName Sheet 名
     * @param rowType 行模型类型
     * @param batches 数据批次
     * @return Sheet 导出定义
     */
    public static ExportSheet of(
            String sheetName, Class<?> rowType, Iterable<? extends Collection<?>> batches) {
        return new ExportSheet(sheetName, rowType, batches);
    }

    /** @return Sheet 名 */
    public String sheetName() {
        return sheetName;
    }

    /** @return 行模型类型 */
    public Class<?> rowType() {
        return rowType;
    }

    /** @return 数据批次 */
    public Iterable<? extends Collection<?>> batches() {
        return batches;
    }
}
