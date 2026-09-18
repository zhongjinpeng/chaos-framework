package com.michael.chaos.security.api.access;

import java.util.Set;

/**
 * 配置形态的 ABAC 属性条件定义。
 *
 * <p>与 {@link AttributeCondition} 的区别是属性引用用字符串表达（{@code subject.tenantId}），
 * 便于从 YAML、数据库或配置中心读取；由 {@link AccessPolicyFactory} 转换为可执行条件。</p>
 *
 * @param left 左侧属性引用表达式
 * @param operator 操作符
 * @param right 右侧属性引用表达式，与 values 二选一
 * @param values 比较用的固定值集合
 */
public record ConditionDefinition(String left, AttributeOperator operator, String right, Set<String> values) {

    /**
     * 规范化条件定义。
     */
    public ConditionDefinition {
        left = AccessCollections.normalizeString(left);
        operator = operator == null ? AttributeOperator.EQ : operator;
        right = AccessCollections.normalizeString(right);
        values = AccessCollections.copyStrings(values);
    }

    /**
     * 创建与固定值比较的条件定义。
     */
    public static ConditionDefinition of(String left, AttributeOperator operator, Set<String> values) {
        return new ConditionDefinition(left, operator, null, values);
    }

    /**
     * 创建两个属性互相比较的条件定义。
     */
    public static ConditionDefinition of(String left, AttributeOperator operator, String right) {
        return new ConditionDefinition(left, operator, right, Set.of());
    }
}
