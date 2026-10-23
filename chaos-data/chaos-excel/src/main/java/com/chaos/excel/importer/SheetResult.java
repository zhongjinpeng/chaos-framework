package com.chaos.excel.importer;

/**
 * 单个 Sheet 的导入统计。
 *
 * @param sheetName Sheet 名
 * @param totalRows 数据行数
 * @param successRows 成功行数
 * @param failedRows 失败行数
 * @param skipped 是否因为 Sheet 不存在而跳过
 */
public record SheetResult(
        String sheetName,
        long totalRows,
        long successRows,
        long failedRows,
        boolean skipped) {
}
