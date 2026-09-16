package com.michael.chaos.mybatis.datascope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据权限条件。
 *
 * <p>一个条件可以包含多个字段规则，规则之间默认使用 AND 连接。保留 `column + values`
 * 构造器是为了兼容旧版本单字段 IN 场景。</p>
 *
 * @param rules 数据权限字段规则
 */
public record DataScopeCondition(List<Rule> rules) {

    /**
     * 兼容旧版本单字段 IN 条件。
     */
    public DataScopeCondition(String column, List<String> values) {
        this(List.of(Rule.in(column, values)));
    }

    /**
     * 规范化数据权限规则集合。
     */
    public DataScopeCondition {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * 创建单字段等值条件。
     */
    public static DataScopeCondition eq(String column, String value) {
        return new DataScopeCondition(List.of(Rule.eq(column, value)));
    }

    /**
     * 创建单字段 IN 条件。
     */
    public static DataScopeCondition in(String column, List<String> values) {
        return new DataScopeCondition(List.of(Rule.in(column, values)));
    }

    /**
     * 创建单字段 LIKE 条件。
     */
    public static DataScopeCondition like(String column, String value) {
        return new DataScopeCondition(List.of(Rule.like(column, value)));
    }

    /**
     * 创建单字段 BETWEEN 条件。
     */
    public static DataScopeCondition between(String column, String startValue, String endValue) {
        return new DataScopeCondition(List.of(Rule.between(column, startValue, endValue)));
    }

    /**
     * 是否为空条件。
     */
    public boolean isEmpty() {
        return rules.isEmpty() || rules.stream().allMatch(Rule::isEmpty);
    }

    /**
     * 数据权限字段规则。
     *
     * @param column 字段名
     * @param tableAlias 表别名；为空时使用当前表或 SQL 中的别名
     * @param operator 操作符
     * @param values 条件值
     */
    public record Rule(
            String column,
            String tableAlias,
            Operator operator,
            List<String> values
    ) {

        /**
         * 规范化规则字段。
         */
        public Rule {
            column = Objects.requireNonNullElse(column, "").trim();
            tableAlias = Objects.requireNonNullElse(tableAlias, "").trim();
            operator = Objects.requireNonNullElse(operator, Operator.IN);
            values = values == null ? List.of() : normalizeValues(values);
        }

        /**
         * 创建等值规则。
         */
        public static Rule eq(String column, String value) {
            return new Rule(column, "", Operator.EQ, List.of(value));
        }

        /**
         * 创建带表别名的等值规则。
         */
        public static Rule eq(String tableAlias, String column, String value) {
            return new Rule(column, tableAlias, Operator.EQ, List.of(value));
        }

        /**
         * 创建 IN 规则。
         */
        public static Rule in(String column, List<String> values) {
            return new Rule(column, "", Operator.IN, values);
        }

        /**
         * 创建带表别名的 IN 规则。
         */
        public static Rule in(String tableAlias, String column, List<String> values) {
            return new Rule(column, tableAlias, Operator.IN, values);
        }

        /**
         * 创建 LIKE 规则。
         */
        public static Rule like(String column, String value) {
            return new Rule(column, "", Operator.LIKE, List.of(value));
        }

        /**
         * 创建带表别名的 LIKE 规则。
         */
        public static Rule like(String tableAlias, String column, String value) {
            return new Rule(column, tableAlias, Operator.LIKE, List.of(value));
        }

        /**
         * 创建 BETWEEN 规则。
         */
        public static Rule between(String column, String startValue, String endValue) {
            return new Rule(column, "", Operator.BETWEEN, List.of(startValue, endValue));
        }

        /**
         * 创建带表别名的 BETWEEN 规则。
         */
        public static Rule between(String tableAlias, String column, String startValue, String endValue) {
            return new Rule(column, tableAlias, Operator.BETWEEN, List.of(startValue, endValue));
        }

        /**
         * 是否为空规则。
         */
        public boolean isEmpty() {
            int requiredSize = operator == Operator.BETWEEN ? 2 : 1;
            return column.isBlank() || values.size() < requiredSize;
        }

        private static List<String> normalizeValues(List<String> values) {
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    normalized.add(trimmed);
                }
            }
            return List.copyOf(normalized);
        }
    }

    /**
     * 数据权限条件操作符。
     */
    public enum Operator {
        /**
         * 等值匹配。
         */
        EQ,
        /**
         * IN 集合匹配。
         */
        IN,
        /**
         * LIKE 模糊匹配。
         */
        LIKE,
        /**
         * BETWEEN 范围匹配。
         */
        BETWEEN
    }
}
