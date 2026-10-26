package com.chaos.core.importing;

import java.util.Objects;

/**
 * 单行导入校验失败，携带稳定错误码和关联列名。
 *
 * <p>该异常是应用层与文件解析实现之间的约定：应用层抛出后由基础设施适配器转换为文件框架的
 * 行级错误，最终进入错误文件。</p>
 */
public class ImportRowValidationException extends RuntimeException {

    private final String code;
    private final String columnName;

    /**
     * 创建行级校验异常。
     *
     * @param code 稳定错误码，进入错误文件的“错误码”列
     * @param columnName 关联列名，可为空表示整行错误
     * @param message 面向用户的错误原因，不得包含 SQL、堆栈或内部路径
     */
    public ImportRowValidationException(String code, String columnName, String message) {
        super(Objects.requireNonNull(message, "message 不能为空"));
        this.code = Objects.requireNonNull(code, "code 不能为空");
        this.columnName = columnName;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 业务或格式错误码
     */
    public String code() {
        return code;
    }

    /**
     * 返回关联列名。
     *
     * @return 列名；整行错误时为 {@code null}
     */
    public String columnName() {
        return columnName;
    }
}
