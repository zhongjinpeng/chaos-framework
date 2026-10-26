package com.chaos.core.importing;

import java.util.Objects;

/**
 * 整份导入文件被拒绝，用于表达与具体行无关的文件级失败。
 *
 * <p>文件解析实现把框架异常转换为该异常，应用层据此生成稳定的业务错误码和用户提示，
 * 从而不依赖文件框架的异常类型。</p>
 */
public class ImportRejectedException extends RuntimeException {

    /**
     * 文件级失败原因。
     */
    public enum Reason {
        /**
         * 表头与行模型列定义不一致：缺列、多列、改名或换序。
         */
        HEADER_MISMATCH,
        /**
         * 数据行数超过单次导入上限。
         */
        ROW_LIMIT_EXCEEDED,
        /**
         * 工作簿结构损坏、压缩包异常或无法解析。
         */
        UNREADABLE,
        /**
         * 扩展名、文件大小或内容不符合导入要求。
         */
        INVALID_FILE
    }

    private final Reason reason;
    private final int maxRows;

    /**
     * 创建不携带行数上限的文件级拒绝异常。
     *
     * @param reason 失败原因
     * @param message 面向用户的失败说明
     */
    public ImportRejectedException(Reason reason, String message) {
        this(reason, message, 0);
    }

    /**
     * 创建文件级拒绝异常。
     *
     * @param reason 失败原因
     * @param message 面向用户的失败说明
     * @param maxRows 超限场景下的允许行数，其他场景传 0
     */
    public ImportRejectedException(Reason reason, String message, int maxRows) {
        super(Objects.requireNonNull(message, "message 不能为空"));
        this.reason = Objects.requireNonNull(reason, "reason 不能为空");
        this.maxRows = maxRows;
    }

    /**
     * 返回文件级失败原因。
     *
     * @return 失败原因
     */
    public Reason reason() {
        return reason;
    }

    /**
     * 返回允许的最大数据行数。
     *
     * @return 行数上限；非超限场景返回 0
     */
    public int maxRows() {
        return maxRows;
    }

    /**
     * 返回可直接展示给用户、且不包含内部实现细节的失败原因。
     *
     * @return 按失败类型生成的中文提示；调用方可直接作为业务异常消息返回
     */
    public String userMessage() {
        return switch (reason) {
            case HEADER_MISMATCH -> "导入文件表头与模板不一致，请下载最新模板后重新填写";
            case ROW_LIMIT_EXCEEDED -> "单次导入不能超过 " + maxRows + " 行，请拆分文件后导入";
            case UNREADABLE, INVALID_FILE -> "无法解析工作簿，请使用最新导入模板";
        };
    }
}
