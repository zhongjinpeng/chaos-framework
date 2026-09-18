package com.michael.chaos.security.api.access;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * 把配置形态的 {@link PolicyDefinition} 转换为可执行的 {@link AuthorizationPolicy}。
 *
 * <p>转换期做全部校验，配置写错时抛 {@link ChaosDiagnosticException}，消息里直接给出出错的配置路径与改法，
 * 避免运行期出现"策略一直不命中却不知道为什么"。</p>
 */
public final class AccessPolicyFactory {

    private AccessPolicyFactory() {
    }

    /**
     * 批量转换策略定义，跳过 {@code enabled=false} 的定义。
     *
     * @param definitions 策略定义列表
     * @param configPath 配置路径前缀，例如 {@code chaos.security.access.policies}，仅用于错误提示
     */
    public static List<AuthorizationPolicy> create(List<PolicyDefinition> definitions, String configPath) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<AuthorizationPolicy> policies = new ArrayList<>(definitions.size());
        Set<String> seenIds = new LinkedHashSet<>();
        for (int index = 0; index < definitions.size(); index++) {
            PolicyDefinition definition = definitions.get(index);
            String path = path(configPath, index);
            if (definition == null || !definition.enabled()) {
                continue;
            }
            AuthorizationPolicy policy = create(definition, path);
            if (!seenIds.add(definition.id())) {
                throw fail(
                        "授权策略 ID 重复：" + definition.id(),
                        path + ".id 与前面的策略重复，决策原因里将无法区分是哪条策略生效",
                        "给 " + path + ".id 换一个唯一名字，例如 " + definition.id() + "-2");
            }
            policies.add(policy);
        }
        return List.copyOf(policies);
    }

    /**
     * 转换单条策略定义。
     *
     * @param definition 策略定义
     * @param configPath 配置路径，例如 {@code chaos.security.access.policies[0]}，仅用于错误提示
     */
    public static AuthorizationPolicy create(PolicyDefinition definition, String configPath) {
        String path = configPath == null || configPath.isBlank() ? "policies" : configPath.trim();
        if (definition == null) {
            throw fail(
                    "授权策略定义为空",
                    path + " 没有任何内容",
                    "删除该条配置，或补全 id / actions / conditions");
        }
        if (definition.id().isBlank()) {
            throw fail(
                    "授权策略缺少 ID",
                    path + ".id 为空，拒绝原因里无法定位是哪条策略",
                    "给 " + path + ".id 起一个业务可读的名字，例如 order-owner-only");
        }
        if (definition.actions().isEmpty() && definition.conditions().isEmpty()) {
            throw fail(
                    "授权策略 " + definition.id() + " 既没有动作也没有条件",
                    path + " 会对所有请求生效，等同于全局放行或全局拒绝",
                    "补充 " + path + ".actions（如 order:read），或补充 " + path + ".conditions");
        }
        List<AttributeCondition> conditions = new ArrayList<>(definition.conditions().size());
        for (int index = 0; index < definition.conditions().size(); index++) {
            conditions.add(create(definition, definition.conditions().get(index), path + ".conditions[" + index + "]"));
        }
        return new AbacAuthorizationPolicy(
                definition.id(),
                actions(definition),
                conditions,
                definition.effect());
    }

    private static AttributeCondition create(
            PolicyDefinition definition,
            ConditionDefinition condition,
            String path) {
        if (condition == null) {
            throw fail(
                    "授权策略 " + definition.id() + " 存在空条件",
                    path + " 没有任何内容",
                    "删除该条条件，或补全 left / operator / values");
        }
        AttributeReference left = reference(definition, condition.left(), path + ".left");
        AttributeOperator operator = condition.operator();
        boolean hasRight = !condition.right().isBlank();
        boolean hasValues = !condition.values().isEmpty();
        if (operator.maxValues() == 0 && (hasRight || hasValues)) {
            throw fail(
                    "授权策略 " + definition.id() + " 的 " + operator + " 条件不需要比较值",
                    path + " 同时配置了 " + operator + " 和比较值",
                    "删除 " + path + ".values 与 " + path + ".right，或把 operator 换成 EQ/IN 等需要比较值的操作符");
        }
        if (operator.requiresComparand() && !hasRight && !hasValues) {
            throw fail(
                    "授权策略 " + definition.id() + " 的 " + operator + " 条件缺少比较值",
                    path + " 既没有 values 也没有 right",
                    "补充 " + path + ".values（固定值），或补充 " + path + ".right（另一个属性引用）");
        }
        if (hasRight && hasValues) {
            throw fail(
                    "授权策略 " + definition.id() + " 的条件同时配置了 right 和 values",
                    path + " 无法判断该和属性比还是和固定值比",
                    "只保留 " + path + ".right 或 " + path + ".values 其中之一");
        }
        if (hasRight && !operator.supportsReference()) {
            throw fail(
                    "授权策略 " + definition.id() + " 的 " + operator + " 条件不支持属性对属性比较",
                    path + ".right 只能用于 EQ/NOT_EQ/GT/GTE/LT/LTE",
                    "把 " + path + ".right 换成 " + path + ".values，或改用支持属性比较的操作符");
        }
        if (hasValues && condition.values().size() < operator.minValues()) {
            throw fail(
                    "授权策略 " + definition.id() + " 的 " + operator + " 条件需要 " + operator.minValues() + " 个比较值",
                    path + ".values 实际配置了 " + condition.values().size() + " 个值",
                    "把 " + path + ".values 配成 " + operator.minValues() + " 个值，例如 [\"09:00\", \"18:00\"]");
        }
        if (condition.values().size() > operator.maxValues()) {
            throw fail(
                    "授权策略 " + definition.id() + " 的 " + operator + " 条件最多支持 " + operator.maxValues() + " 个比较值",
                    path + ".values 实际配置了 " + condition.values().size() + " 个值",
                    "只保留 " + operator.maxValues() + " 个值，或把 operator 换成 IN / NOT_IN");
        }
        if (operator == AttributeOperator.REGEX) {
            validateRegex(definition, condition.values(), path);
        }
        AttributeReference right = hasRight ? reference(definition, condition.right(), path + ".right") : null;
        return new AttributeCondition(left, operator, right, condition.values());
    }

    private static void validateRegex(PolicyDefinition definition, Set<String> values, String path) {
        for (String regex : values) {
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException ex) {
                throw fail(
                        "授权策略 " + definition.id() + " 的正则表达式非法：" + regex,
                        path + ".values 中的正则无法编译：" + ex.getDescription(),
                        "修正 " + path + ".values 中的正则表达式");
            }
        }
    }

    private static AttributeReference reference(PolicyDefinition definition, String expression, String path) {
        try {
            return AttributeReference.parse(expression);
        } catch (IllegalArgumentException ex) {
            throw fail(
                    "授权策略 " + definition.id() + " 的属性引用为空",
                    path + " 没有配置属性引用",
                    "填写属性引用，例如 subject.tenantId、resource.ownerId、environment.clientIp");
        }
    }

    private static Set<AuthorizationAction> actions(PolicyDefinition definition) {
        if (definition.actions().isEmpty()) {
            return Set.of(AuthorizationAction.ANY);
        }
        return definition.actions().stream()
                .map(AuthorizationAction::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String path(String configPath, int index) {
        String prefix = configPath == null || configPath.isBlank() ? "policies" : configPath.trim();
        return prefix + "[" + index + "]";
    }

    private static ChaosDiagnosticException fail(String problem, String cause, String fix) {
        return new ChaosDiagnosticException(ChaosDiagnostic.of(problem, cause, fix));
    }
}
