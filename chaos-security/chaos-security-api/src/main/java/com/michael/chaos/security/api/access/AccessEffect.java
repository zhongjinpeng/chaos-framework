package com.michael.chaos.security.api.access;

/**
 * 访问控制决策结果。
 */
public enum AccessEffect {
    /**
     * 明确允许访问。
     */
    ALLOW,
    /**
     * 明确拒绝访问。
     */
    DENY,
    /**
     * 当前策略不适用。
     */
    ABSTAIN
}
