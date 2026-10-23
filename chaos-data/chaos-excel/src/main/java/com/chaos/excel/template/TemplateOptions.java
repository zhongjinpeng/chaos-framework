package com.chaos.excel.template;

/**
 * 模板生成选项。
 *
 * @param samplePlacement 示例数据放置方式
 * @param includeInstructions 是否生成填写说明页
 * @param headerRows 表头行数
 * @param lastDataRow 数据校验覆盖的最后一行（0-based）
 * @param maxSheets 最大数据 Sheet 数
 * @param maxSampleRows 每个 Sheet 最大示例行数
 */
public record TemplateOptions(
        SamplePlacement samplePlacement,
        boolean includeInstructions,
        int headerRows,
        int lastDataRow,
        int maxSheets,
        int maxSampleRows) {

    public TemplateOptions {
        if (samplePlacement == null) {
            throw new IllegalArgumentException("samplePlacement must not be null");
        }
        if (headerRows < 1) {
            throw new IllegalArgumentException("headerRows must be greater than 0");
        }
        if (lastDataRow < headerRows) {
            throw new IllegalArgumentException("lastDataRow must not be before the data area");
        }
        if (maxSheets < 1 || maxSampleRows < 0) {
            throw new IllegalArgumentException("Template limits must be positive");
        }
    }

    /** @return 独立示例页、无说明页、单行表头、4999 行校验范围 */
    public static TemplateOptions defaults() {
        return new TemplateOptions(SamplePlacement.SEPARATE_SHEET, false, 1, 4999, 10, 1_000);
    }

    /** @return 修改示例放置方式后的选项 */
    public TemplateOptions samplePlacement(SamplePlacement value) {
        return new TemplateOptions(value, includeInstructions, headerRows, lastDataRow,
                maxSheets, maxSampleRows);
    }

    /** @return 修改说明页开关后的选项 */
    public TemplateOptions includeInstructions(boolean value) {
        return new TemplateOptions(samplePlacement, value, headerRows, lastDataRow,
                maxSheets, maxSampleRows);
    }

    /** @return 修改表头行数后的选项 */
    public TemplateOptions headerRows(int value) {
        return new TemplateOptions(samplePlacement, includeInstructions, value, lastDataRow,
                maxSheets, maxSampleRows);
    }

    /** @return 修改校验最后一行后的选项 */
    public TemplateOptions lastDataRow(int value) {
        return new TemplateOptions(samplePlacement, includeInstructions, headerRows, value,
                maxSheets, maxSampleRows);
    }

    /** @return 修改最大 Sheet 数后的选项 */
    public TemplateOptions maxSheets(int value) {
        return new TemplateOptions(samplePlacement, includeInstructions, headerRows, lastDataRow,
                value, maxSampleRows);
    }

    /** @return 修改最大示例行数后的选项 */
    public TemplateOptions maxSampleRows(int value) {
        return new TemplateOptions(samplePlacement, includeInstructions, headerRows, lastDataRow,
                maxSheets, value);
    }
}
