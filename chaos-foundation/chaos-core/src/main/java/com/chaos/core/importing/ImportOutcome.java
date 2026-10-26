package com.chaos.core.importing;

import com.chaos.core.file.AttachmentFile;

/**
 * 导入用例结果，统一表达数据行总数、成功行数、失败行数和可选错误文件。
 *
 * <p>解析结果 {@link ImportResult} 只描述文件读取阶段；本类型描述应用用例最终结果。
 * 采用全量回滚策略时，存在错误行的文件不会写入任何数据，因此成功行数为 0。</p>
 *
 * @param totalRows 文件中的数据行数，不含表头
 * @param successRows 实际写入的数据行数
 * @param failedRows 校验或写入失败的数据行数
 * @param errorFile 错误文件；全部成功时为 {@code null}
 */
public record ImportOutcome(
        long totalRows, long successRows, long failedRows, AttachmentFile errorFile)
    implements ImportResultView {

    /**
     * 校验行数统计不出现负数。
     */
    public ImportOutcome {
        if (totalRows < 0 || successRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("导入行数统计不能为负数");
        }
    }

    /**
     * 判断是否存在错误行。
     *
     * @return 失败行数大于 0 时返回 {@code true}
     */
    public boolean hasErrors() {
        return failedRows > 0;
    }

    /**
     * 判断本次导入是否全部成功。
     *
     * @return 不存在失败行时返回 {@code true}
     */
    public boolean isSuccess() {
        return !hasErrors();
    }

    /**
     * 判断是否需要向调用方返回错误文件。
     *
     * @return 错误文件不为 {@code null} 时返回 {@code true}
     */
    public boolean hasErrorFile() {
        return errorFile != null;
    }
}
