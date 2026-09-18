package com.michael.chaos.security.api.access;

/**
 * 单个操作符的匹配实现，由 {@link AttributeOperator} 常量持有。
 */
@FunctionalInterface
interface AttributeMatcher {

    /**
     * 判断条件是否匹配。
     */
    boolean matches(AttributeMatchContext context);
}
