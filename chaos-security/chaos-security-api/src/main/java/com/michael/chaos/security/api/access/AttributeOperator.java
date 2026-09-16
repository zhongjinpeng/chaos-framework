package com.michael.chaos.security.api.access;

/**
 * ABAC 属性操作符。
 */
public enum AttributeOperator {
    /**
     * 属性必须存在。
     */
    EXISTS,
    /**
     * 属性必须不存在。
     */
    NOT_EXISTS,
    /**
     * 属性等值匹配。
     */
    EQ,
    /**
     * 属性不等值匹配。
     */
    NOT_EQ,
    /**
     * 属性属于指定集合。
     */
    IN,
    /**
     * 属性不属于指定集合。
     */
    NOT_IN
}
