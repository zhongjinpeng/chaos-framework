package com.chaos.core.importing;

import com.chaos.core.file.AttachmentFile;

import java.util.List;

/**
 * 导入解析结果，供应用层判断成功、失败以及是否需要返回错误文件。
 *
 * @param totalRows 文件中的数据行数，不含表头
 * @param successRows 解析并完成业务处理的行数
 * @param failedRows 校验失败的行数
 * @param errors 有界错误明细，条数达到上限后由框架截断
 * @param errorsTruncated 是否因为错误条数上限而截断
 * @param errorFile 错误文件；没有错误行时为 {@code null}
 */
public record ImportResult(
        long totalRows,
        long successRows,
        long failedRows,
        List<ImportRowError> errors,
        boolean errorsTruncated,
        AttachmentFile errorFile) {

    public ImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * 判断是否存在错误行。
     *
     * @return 存在失败行或错误明细时返回 {@code true}
     */
    public boolean hasErrors() {
        return failedRows > 0 || !errors.isEmpty();
    }

    /**
     * 判断是否全部成功。
     *
     * @return 没有错误行时返回 {@code true}
     */
    public boolean isSuccess() {
        return !hasErrors();
    }

    /**
     * 判断是否需要向调用方返回错误文件。
     *
     * @return 存在错误文件时返回 {@code true}
     */
    public boolean hasErrorFile() {
        return errorFile != null;
    }
}
