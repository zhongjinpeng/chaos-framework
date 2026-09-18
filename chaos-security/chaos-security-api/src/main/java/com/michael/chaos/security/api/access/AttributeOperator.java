package com.michael.chaos.security.api.access;

/**
 * ABAC 属性操作符。
 *
 * <p>每个常量自带匹配实现（{@link AttributeMatcher}）和对比较值的要求（{@link ComparandSpec}）：
 * 匹配逻辑不再是 {@code AttributeCondition} 里的一个大 switch，配置校验规则也不再是
 * {@code AccessPolicyFactory} 里的几张硬编码表——新增操作符只需在这里加一个常量。</p>
 */
public enum AttributeOperator {
    /**
     * 属性必须存在。
     */
    EXISTS(AttributeMatchers::exists, ComparandSpec.none()),
    /**
     * 属性必须不存在。
     */
    NOT_EXISTS(AttributeMatchers::notExists, ComparandSpec.none()),
    /**
     * 属性等值匹配。
     */
    EQ(AttributeMatchers::equalTo, ComparandSpec.valuesOrReference()),
    /**
     * 属性不等值匹配。
     */
    NOT_EQ(AttributeMatchers::notEqualTo, ComparandSpec.valuesOrReference()),
    /**
     * 属性属于指定集合。
     */
    IN(AttributeMatchers::in, ComparandSpec.values()),
    /**
     * 属性不属于指定集合。
     */
    NOT_IN(AttributeMatchers::notIn, ComparandSpec.values()),
    /**
     * 属性大于比较值。
     */
    GT(AttributeMatchers::greaterThan, ComparandSpec.singleOrReference()),
    /**
     * 属性大于等于比较值。
     */
    GTE(AttributeMatchers::greaterThanOrEqual, ComparandSpec.singleOrReference()),
    /**
     * 属性小于比较值。
     */
    LT(AttributeMatchers::lessThan, ComparandSpec.singleOrReference()),
    /**
     * 属性小于等于比较值。
     */
    LTE(AttributeMatchers::lessThanOrEqual, ComparandSpec.singleOrReference()),
    /**
     * 属性落在两个比较值之间（闭区间，与两个值的先后顺序无关）。
     */
    BETWEEN(AttributeMatchers::between, ComparandSpec.pair()),
    /**
     * 属性完整匹配任意一条正则表达式。
     */
    REGEX(AttributeMatchers::regex, ComparandSpec.values()),
    /**
     * 集合属性包含全部指定值，字符串属性包含全部指定子串。
     */
    CONTAINS(AttributeMatchers::contains, ComparandSpec.values());

    private final AttributeMatcher matcher;

    private final ComparandSpec comparandSpec;

    AttributeOperator(AttributeMatcher matcher, ComparandSpec comparandSpec) {
        this.matcher = matcher;
        this.comparandSpec = comparandSpec;
    }

    /**
     * 是否必须提供比较值或右侧属性引用。
     */
    public boolean requiresComparand() {
        return comparandSpec.required();
    }

    /**
     * 是否支持属性对属性比较。
     */
    public boolean supportsReference() {
        return comparandSpec.referenceSupported();
    }

    /**
     * 固定值最少个数。
     */
    public int minValues() {
        return comparandSpec.minValues();
    }

    /**
     * 固定值最多个数。
     */
    public int maxValues() {
        return comparandSpec.maxValues();
    }

    /**
     * 执行匹配。
     */
    boolean matches(AttributeMatchContext context) {
        return matcher.matches(context);
    }
}
