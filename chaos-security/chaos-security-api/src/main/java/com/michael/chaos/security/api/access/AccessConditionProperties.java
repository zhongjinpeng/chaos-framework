package com.michael.chaos.security.api.access;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ABAC 属性条件配置。
 */
public class AccessConditionProperties {

    /**
     * 左侧属性引用，例如 subject.tenantId、resource.ownerId、environment.clientIp。
     */
    private String left;

    /**
     * 比较操作符。
     */
    private AttributeOperator operator = AttributeOperator.EQ;

    /**
     * 右侧属性引用，与 values 二选一，仅 EQ/NOT_EQ/GT/GTE/LT/LTE 支持。
     */
    private String right;

    /**
     * 比较用的固定值，与 right 二选一。
     */
    private List<String> values = List.of();

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public AttributeOperator getOperator() {
        return operator;
    }

    public void setOperator(AttributeOperator operator) {
        this.operator = operator == null ? AttributeOperator.EQ : operator;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
    }

    public List<String> getValues() {
        return List.copyOf(values);
    }

    public void setValues(List<String> values) {
        this.values = values == null ? List.of() : List.copyOf(values);
    }

    /**
     * 转换为框架内部的条件定义。
     */
    public ConditionDefinition toDefinition() {
        Set<String> copiedValues = new LinkedHashSet<>(values);
        return new ConditionDefinition(left, operator, right, copiedValues);
    }
}
