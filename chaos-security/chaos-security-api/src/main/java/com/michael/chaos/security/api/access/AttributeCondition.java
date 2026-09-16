package com.michael.chaos.security.api.access;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
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
     * 判断条件是否匹配。
     */
    public boolean matches(AuthorizationRequest request) {
        Object leftValue = left == null ? null : left.resolve(request).orElse(null);
        Object rightValue = right == null ? null : right.resolve(request).orElse(null);
        return switch (operator) {
            case EXISTS -> leftValue != null;
            case NOT_EXISTS -> leftValue == null;
            case EQ -> matchesEquals(leftValue, rightValue);
            case NOT_EQ -> matchesNotEquals(leftValue, rightValue);
            case IN -> matchesIn(leftValue);
            case NOT_IN -> matchesNotIn(leftValue);
        };
    }

    private boolean matchesEquals(Object leftValue, Object rightValue) {
        if (right != null) {
            return leftValue != null
                    && rightValue != null
                    && Objects.equals(normalizedValue(leftValue), normalizedValue(rightValue));
        }
        return leftValue != null && values.stream().anyMatch(value -> Objects.equals(normalizedValue(leftValue), value));
    }

    private boolean matchesNotEquals(Object leftValue, Object rightValue) {
        if (right != null) {
            return leftValue != null && rightValue != null && !matchesEquals(leftValue, rightValue);
        }
        return leftValue != null && !values.isEmpty()
                && values.stream().noneMatch(value -> Objects.equals(normalizedValue(leftValue), value));
    }

    private boolean matchesIn(Object leftValue) {
        if (leftValue == null) {
            return false;
        }
        if (leftValue instanceof Collection<?> collection) {
            return collection.stream().map(this::normalizedValue).anyMatch(values::contains);
        }
        return values.contains(normalizedValue(leftValue));
    }

    private boolean matchesNotIn(Object leftValue) {
        return leftValue != null && !values.isEmpty() && !matchesIn(leftValue);
    }

    private String normalizedValue(Object value) {
        return AccessCollections.normalizeString(value);
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
