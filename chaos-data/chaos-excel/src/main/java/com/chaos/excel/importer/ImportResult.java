package com.chaos.excel.importer;

import com.chaos.excel.ExcelFile;
import com.chaos.excel.error.ImportErrorReport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 多 Sheet 导入结果。
 *
 * @param originalFilename 上传文件名
 * @param totalRows 数据行数
 * @param successRows 成功行数
 * @param failedRows 失败行数
 * @param errors 有界错误列表
 * @param errorsTruncated 是否因为错误上限而截断
 * @param sheets 按 Sheet 名的统计
 * @param sheetHeaders 按 Sheet 名的原始表头
 */
public record ImportResult(
        String originalFilename,
        long totalRows,
        long successRows,
        long failedRows,
        List<ImportError> errors,
        boolean errorsTruncated,
        Map<String, SheetResult> sheets,
        Map<String, List<String>> sheetHeaders) {

    public ImportResult {
        Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        errors = errors == null ? List.of() : List.copyOf(errors);
        sheets = sheets == null ? Map.of() : Map.copyOf(sheets);
        sheetHeaders = sheetHeaders == null ? Map.of() : Map.copyOf(sheetHeaders);
    }

    /** @return 是否存在错误 */
    public boolean hasErrors() {
        return failedRows > 0 || !errors.isEmpty();
    }

    /** @return 是否全部成功 */
    public boolean isSuccess() {
        return !hasErrors();
    }

    /**
     * 延迟生成错误文件；没有错误时返回空。
     *
     * @return 可流式下载的错误文件
     */
    public Optional<ExcelFile> errorFile() {
        return ImportErrorReport.create(this);
    }

    /** @return 接口响应摘要 */
    public ImportSummary summary() {
        return ImportSummary.from(this);
    }
}
