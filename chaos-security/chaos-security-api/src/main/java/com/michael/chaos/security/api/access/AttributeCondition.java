package com.michael.chaos.security.api.access;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ABAC 属性条件。
 *
 * @param left 左侧属性引用
 * @param operator 操作符
 * @param right 右侧属性引用
 * @param values 固定值集合
 */
public record AttributeCondition(
        AttributeReference left,
        AttributeOperator operator,
        AttributeReference right,
        Set<String> values
) {

    /**
     * 规范化属性条件。
     */
    public AttributeCondition {
        operator = operator == null ? AttributeOperator.EQ : operator;
        values = AccessCollections.copyStrings(values);
    }

    /**
     * 左右属性相等。
     */
    public static AttributeCondition eq(AttributeReference left, AttributeReference right) {
        return new AttributeCondition(left, AttributeOperator.EQ, right, Set.of());
    }

    /**
     * 属性等于固定值。
     */
    public static AttributeCondition eq(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.EQ, null, singleValue(value));
    }

    /**
     * 左右属性不相等。
     */
    public static AttributeCondition notEq(AttributeReference left, AttributeReference right) {
        return new AttributeCondition(left, AttributeOperator.NOT_EQ, right, Set.of());
    }

    /**
     * 属性不等于固定值。
     */
    public static AttributeCondition notEq(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.NOT_EQ, null, singleValue(value));
    }

    /**
     * 属性属于固定值集合。
     */
    public static AttributeCondition in(AttributeReference left, Set<String> values) {
        return new AttributeCondition(left, AttributeOperator.IN, null, values);
    }

    /**
     * 属性不属于固定值集合。
     */
    public static AttributeCondition notIn(AttributeReference left, Set<String> values) {
        return new AttributeCondition(left, AttributeOperator.NOT_IN, null, values);
    }

    /**
     * 属性必须存在。
     */
    public static AttributeCondition exists(AttributeReference left) {
        return new AttributeCondition(left, AttributeOperator.EXISTS, null, Set.of());
    }

    /**
     * 属性必须不存在。
     */
    public static AttributeCondition notExists(AttributeReference left) {
        return new AttributeCondition(left, AttributeOperator.NOT_EXISTS, null, Set.of());
    }

    /**
     * 属性大于固定值。
     */
    public static AttributeCondition gt(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.GT, null, singleValue(value));
    }

    /**
     * 属性大于等于固定值。
     */
    public static AttributeCondition gte(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.GTE, null, singleValue(value));
    }

    /**
     * 属性小于固定值。
     */
    public static AttributeCondition lt(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.LT, null, singleValue(value));
    }

    /**
     * 属性小于等于固定值。
     */
    public static AttributeCondition lte(AttributeReference left, String value) {
        return new AttributeCondition(left, AttributeOperator.LTE, null, singleValue(value));
    }

    /**
     * 属性落在闭区间内。
     */
    public static AttributeCondition between(AttributeReference left, String from, String to) {
        Set<String> bounds = new LinkedHashSet<>();
        bounds.add(AccessCollections.normalizeString(from));
        bounds.add(AccessCollections.normalizeString(to));
        return new AttributeCondition(left, AttributeOperator.BETWEEN, null, bounds);
    }

    /**
     * 属性完整匹配正则表达式。
     */
    public static AttributeCondition regex(AttributeReference left, String regex) {
        return new AttributeCondition(left, AttributeOperator.REGEX, null, singleValue(regex));
    }

    /**
     * 属性包含全部指定值。
     */
    public static AttributeCondition contains(AttributeReference left, Set<String> values) {
        return new AttributeCondition(left, AttributeOperator.CONTAINS, null, values);
    }

    /**
     * 判断条件是否匹配，具体规则由 {@link AttributeOperator} 常量自带的匹配实现决定。
     */
    public boolean matches(AuthorizationRequest request) {
        return operator.matches(new AttributeMatchContext(
                resolve(left, request),
                resolve(right, request),
                right != null,
                values));
    }

    private static Object resolve(AttributeReference reference, AuthorizationRequest request) {
        return reference == null ? null : reference.resolve(request).orElse(null);
    }

    private static Set<String> singleValue(String value) {
        String normalized = AccessCollections.normalizeString(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        values.add(normalized);
        return values;
    }
}
