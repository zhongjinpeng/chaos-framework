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
 * <p>结果对象不可变；{@link #errorFile()} 按需生成并缓存，避免同一结果多次下载时重复构建工作簿。</p>
 */
public final class ImportResult {

    private final String originalFilename;
    private final long totalRows;
    private final long successRows;
    private final long failedRows;
    private final List<ImportError> errors;
    private final boolean errorsTruncated;
    private final Map<String, SheetResult> sheets;
    private final Map<String, List<String>> sheetHeaders;
    private volatile Optional<ExcelFile> errorFile;

    /**
     * 创建导入结果。
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
    public ImportResult(
            String originalFilename,
            long totalRows,
            long successRows,
            long failedRows,
            List<ImportError> errors,
            boolean errorsTruncated,
            Map<String, SheetResult> sheets,
            Map<String, List<String>> sheetHeaders) {
        this.originalFilename =
                Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failedRows = failedRows;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.errorsTruncated = errorsTruncated;
        this.sheets = sheets == null ? Map.of() : Map.copyOf(sheets);
        this.sheetHeaders = copyHeaders(sheetHeaders);
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
        if (headers == null) {
            return Map.of();
        }
        return headers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /** @return 上传文件名 */
    public String originalFilename() {
        return originalFilename;
    }

    /** @return 数据行数 */
    public long totalRows() {
        return totalRows;
    }

    /** @return 成功行数 */
    public long successRows() {
        return successRows;
    }

    /** @return 失败行数 */
    public long failedRows() {
        return failedRows;
    }

    /** @return 有界错误列表 */
    public List<ImportError> errors() {
        return errors;
    }

    /** @return 是否因为错误上限而截断 */
    public boolean errorsTruncated() {
        return errorsTruncated;
    }

    /** @return 按 Sheet 名的统计 */
    public Map<String, SheetResult> sheets() {
        return sheets;
    }

    /** @return 按 Sheet 名的原始表头 */
    public Map<String, List<String>> sheetHeaders() {
        return sheetHeaders;
    }

    /** @return 是否存在错误 */
    public boolean hasErrors() {
        return failedRows > 0 || !errors.isEmpty();
    }

    /** @return 是否全部成功 */
    public boolean isSuccess() {
        return !hasErrors();
    }

    /** @return 是否部分成功 */
    public boolean isPartialSuccess() {
        return successRows > 0 && failedRows > 0;
    }

    /**
     * 成功率；没有任何数据行且没有错误时按 100% 处理。
     *
     * @return 0.0 到 1.0 之间的成功率
     */
    public double successRate() {
        if (totalRows == 0) {
            return hasErrors() ? 0.0 : 1.0;
        }
        return (double) successRows / totalRows;
    }

    /** @return 实际保留的错误条数；可能小于 {@link #failedRows()} */
    public int collectedErrorCount() {
        return errors.size();
    }

    /**
     * 返回指定 Sheet 的错误。
     *
     * @param sheetName Sheet 名
     * @return 该 Sheet 的不可变错误列表
     */
    public List<ImportError> errorsForSheet(String sheetName) {
        if (sheetName == null) {
            return List.of();
        }
        return errors.stream()
                .filter(error -> sheetName.equals(error.sheetName()))
                .toList();
    }

    /**
     * 返回指定 Sheet 的统计。
     *
     * @param sheetName Sheet 名
     * @return 存在时返回统计
     */
    public Optional<SheetResult> sheet(String sheetName) {
        return Optional.ofNullable(sheetName == null ? null : sheets.get(sheetName));
    }

    /**
     * 延迟生成并缓存错误文件；没有错误时返回空。
     *
     * @return 可流式下载的错误文件
     */
    public Optional<ExcelFile> errorFile() {
        Optional<ExcelFile> current = errorFile;
        if (current == null) {
            synchronized (this) {
                current = errorFile;
                if (current == null) {
                    current = ImportErrorReport.create(this);
                    errorFile = current;
                }
            }
        }
        return current;
    }

    /** @return 接口响应摘要 */
    public ImportSummary summary() {
        return ImportSummary.from(this);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ImportResult result)) {
            return false;
        }
        return totalRows == result.totalRows
                && successRows == result.successRows
                && failedRows == result.failedRows
                && errorsTruncated == result.errorsTruncated
                && originalFilename.equals(result.originalFilename)
                && errors.equals(result.errors)
                && sheets.equals(result.sheets)
                && sheetHeaders.equals(result.sheetHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalFilename, totalRows, successRows, failedRows,
                errors, errorsTruncated, sheets, sheetHeaders);
    }

    @Override
    public String toString() {
        return "ImportResult[originalFilename=" + originalFilename
                + ", totalRows=" + totalRows
                + ", successRows=" + successRows
                + ", failedRows=" + failedRows
                + ", errors=" + errors.size()
                + ", errorsTruncated=" + errorsTruncated
                + ", sheets=" + sheets.keySet()
                + "]";
    }
}
