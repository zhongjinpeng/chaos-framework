package com.chaos.core.importing;

import com.chaos.core.file.AttachmentFile;

/**
 * 导入结果的最小只读视图，供 Web 层统一生成“错误文件或成功行数”响应。
 *
 * <p>应用层可以使用框架的 {@link ImportOutcome}，也可以定义模块专用结果类型实现本接口。</p>
 */
public interface ImportResultView {

    /**
     * 返回成功写入的数据行数。
     *
     * @return 成功行数；全量回滚策略下存在错误行时为 0
     */
    long successRows();

    /**
     * 返回错误文件。
     *
     * @return 错误文件；没有错误行时为 {@code null}
     */
    AttachmentFile errorFile();

    /**
     * 判断是否需要返回错误文件。
     *
     * @return 错误文件不为 {@code null} 时返回 {@code true}
     */
    default boolean hasErrorFile() {
        return errorFile() != null;
    }
}
