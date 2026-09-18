package com.michael.chaos.security.api.access;

import java.util.Collection;
import java.util.Iterator;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * {@link AttributeOperator} 各常量的匹配实现。
 *
 * <p>集中放在这里而不是散在 switch 分支里：新增操作符时只要加一个方法并在枚举常量上引用它，
 * 不需要改动 {@link AttributeCondition}。</p>
 */
final class AttributeMatchers {

    private AttributeMatchers() {
    }

    /**
     * 属性存在。
     */
    static boolean exists(AttributeMatchContext context) {
        return context.hasLeftValue();
    }

    /**
     * 属性不存在。
     */
    static boolean notExists(AttributeMatchContext context) {
        return !context.hasLeftValue();
    }

    /**
     * 等值匹配：属性对属性时两侧都要存在，否则命中任意一个固定值即可。
     */
    static boolean equalTo(AttributeMatchContext context) {
        if (!context.hasLeftValue()) {
            return false;
        }
        if (context.referenceComparison()) {
            return context.rightValue() != null && context.leftEquals(context.rightValue());
        }
        return context.values().stream().anyMatch(context::leftEquals);
    }

    /**
     * 不等值匹配。
     */
    static boolean notEqualTo(AttributeMatchContext context) {
        if (!context.hasLeftValue()) {
            return false;
        }
        if (context.referenceComparison()) {
            return context.rightValue() != null && !context.leftEquals(context.rightValue());
        }
        return !context.values().isEmpty() && context.values().stream().noneMatch(context::leftEquals);
    }

    /**
     * 属于固定值集合；左侧是集合时表示交集非空。
     */
    static boolean in(AttributeMatchContext context) {
        if (!context.hasLeftValue()) {
            return false;
        }
        Set<String> values = context.values();
        if (context.leftValue() instanceof Collection<?> collection) {
            return collection.stream().map(AccessCollections::normalizeString).anyMatch(values::contains);
        }
        return values.contains(context.leftText());
    }

    /**
     * 不属于固定值集合。
     */
    static boolean notIn(AttributeMatchContext context) {
        return context.hasLeftValue() && !context.values().isEmpty() && !in(context);
    }

    /**
     * 大于。
     */
    static boolean greaterThan(AttributeMatchContext context) {
        return compare(context, comparison -> comparison > 0);
    }

    /**
     * 大于等于。
     */
    static boolean greaterThanOrEqual(AttributeMatchContext context) {
        return compare(context, comparison -> comparison >= 0);
    }

    /**
     * 小于。
     */
    static boolean lessThan(AttributeMatchContext context) {
        return compare(context, comparison -> comparison < 0);
    }

    /**
     * 小于等于。
     */
    static boolean lessThanOrEqual(AttributeMatchContext context) {
        return compare(context, comparison -> comparison <= 0);
    }

    /**
     * 落在两个边界值之间（闭区间，与边界值先后顺序无关）。
     */
    static boolean between(AttributeMatchContext context) {
        if (!context.hasLeftValue() || context.values().size() != 2) {
            return false;
        }
        Iterator<String> bounds = context.values().iterator();
        OptionalInt toFirst = AttributeValues.compare(context.leftValue(), bounds.next());
        OptionalInt toSecond = AttributeValues.compare(context.leftValue(), bounds.next());
        if (toFirst.isEmpty() || toSecond.isEmpty()) {
            return false;
        }
        int first = toFirst.getAsInt();
        int second = toSecond.getAsInt();
        return (first >= 0 && second <= 0) || (second >= 0 && first <= 0);
    }

    /**
     * 完整匹配任意一条正则表达式。
     */
    static boolean regex(AttributeMatchContext context) {
        return context.hasLeftValue()
                && context.values().stream().anyMatch(regex -> AttributeValues.regexMatches(context.leftValue(), regex));
    }

    /**
     * 集合包含全部指定值，字符串包含全部指定子串。
     */
    static boolean contains(AttributeMatchContext context) {
        if (!context.hasLeftValue() || context.values().isEmpty()) {
            return false;
        }
        if (context.leftValue() instanceof Collection<?> collection) {
            return AccessCollections.copyStrings(collection).containsAll(context.values());
        }
        String actual = context.leftText();
        return context.values().stream().allMatch(actual::contains);
    }

    private static boolean compare(AttributeMatchContext context, IntPredicate predicate) {
        if (!context.hasLeftValue()) {
            return false;
        }
        return context.comparand()
                .map(comparand -> AttributeValues.compare(context.leftValue(), comparand))
                .filter(OptionalInt::isPresent)
                .map(comparison -> predicate.test(comparison.getAsInt()))
                .orElse(false);
    }
}
