package com.michael.chaos.security.api.access;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一次属性条件匹配的输入。
 *
 * @param leftValue 左侧属性值
 * @param rightValue 右侧属性值，仅在属性对属性比较时有值
 * @param referenceComparison 是否属性对属性比较
 * @param values 配置的固定比较值
 */
record AttributeMatchContext(
        Object leftValue,
        Object rightValue,
        boolean referenceComparison,
        Set<String> values
) {

    /**
     * 规范化比较值集合。
     */
    AttributeMatchContext {
        values = values == null ? Set.of() : values;
    }

    /**
     * 左侧属性是否存在。
     */
    boolean hasLeftValue() {
        return leftValue != null;
    }

    /**
     * 左侧属性值的字符串形式。
     */
    String leftText() {
        return AccessCollections.normalizeString(leftValue);
    }

    /**
     * 取比较对象：属性对属性时取右侧属性值，否则取唯一的固定值。
     */
    Optional<Object> comparand() {
        if (referenceComparison) {
            return Optional.ofNullable(rightValue);
        }
        return values.size() == 1 ? Optional.of(values.iterator().next()) : Optional.empty();
    }

    /**
     * 左侧属性值是否等于某个字符串。
     */
    boolean leftEquals(Object other) {
        return Objects.equals(leftText(), AccessCollections.normalizeString(other));
    }
}
