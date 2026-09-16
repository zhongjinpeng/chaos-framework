package com.michael.chaos.mybatis.sql;

import com.baomidou.mybatisplus.annotation.DbType;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 构造 SQL 字符串字面量的安全工具。
 *
 * <p>JSqlParser 的 {@link StringValue#toString()} 只在值两边拼单引号，不做任何转义。租户 ID、数据权限值
 * 直接 {@code new StringValue(value)} 时，{@code x' OR '1'='1} 会改写整条 SQL 的过滤条件。</p>
 *
 * <p>转义规则：</p>
 * <ul>
 *     <li>所有数据库：单引号 {@code '} 转为 {@code ''}（SQL 标准）；</li>
 *     <li>MySQL 同类数据库（MySQL、MariaDB、TiDB、OceanBase MySQL 模式等）：反斜杠 {@code \} 转为 {@code \\}。
 *     这些数据库默认把反斜杠当转义符，只转义单引号时 {@code \'} 仍可闭合字符串；</li>
 *     <li>拒绝 NUL 等控制字符，它们在不同驱动下的截断行为不可预测。</li>
 * </ul>
 */
public final class SqlLiterals {

    private SqlLiterals() {
    }

    /**
     * 按数据库方言转义并创建字符串字面量。
     *
     * @param value 原始值
     * @param dbType 数据库类型，{@code null} 时按 MySQL 规则转义（同时转义反斜杠更保守）
     * @return 已转义的字面量表达式
     * @throws IllegalArgumentException 值包含控制字符
     */
    public static StringValue stringValue(String value, DbType dbType) {
        return new StringValue(escape(value, dbType));
    }

    /**
     * 按数据库方言转义字符串内容（不含两侧引号）。
     */
    public static String escape(String value, DbType dbType) {
        if (value == null) {
            return "";
        }
        boolean escapeBackslash = dbType == null || dbType.mysqlSameType();
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch < 0x20 && ch != '\t') || ch == 0x7f) {
                throw new IllegalArgumentException("SQL literal must not contain control characters");
            }
            if (ch == '\'') {
                builder.append("''");
            } else if (ch == '\\' && escapeBackslash) {
                builder.append("\\\\");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
