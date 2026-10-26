package com.chaos.excel.importer;

import com.chaos.core.importing.ImportRowError;import java.util.List;

/**
 * 导入结果摘要，适合直接作为接口响应体。
 *
 * @param totalRows 数据行数
 * @param successRows 成功行数
 * @param failedRows 失败行数
 * @param hasErrors 是否存在错误
 * @param errors 有界错误列表
 */
public record ImportSummary(
        long totalRows,
        long successRows,
        long failedRows,
        boolean hasErrors,
        List<ImportRowError> errors) {

    public ImportSummary {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * 从导入结果生成摘要。
     *
     * @param result 导入结果
     * @return 摘要
     */
    public static ImportSummary from(ImportResult result) {
        return new ImportSummary(
                result.totalRows(),
                result.successRows(),
                result.failedRows(),
                result.hasErrors(),
                result.errors());
    }
}
