package com.chaos.mybatis.sql;

/**
 * MySQL {@code LIKE} 查询模式转义工具。
 *
 * <p>使用反斜杠作为转义符，适用于未启用 {@code NO_BACKSLASH_ESCAPES} 的 MySQL 兼容数据库。
 * 参数仍应通过 MyBatis 绑定；本工具只负责让 {@code %} 和 {@code _} 按字面匹配。</p>
 */
public final class MySqlLikePatterns {

    private MySqlLikePatterns() {
    }

    /** 转义反斜杠及两个 {@code LIKE} 通配符；{@code null} 原样返回。 */
    public static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
