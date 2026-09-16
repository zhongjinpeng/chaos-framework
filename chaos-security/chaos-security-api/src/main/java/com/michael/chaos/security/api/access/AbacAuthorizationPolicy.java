package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于属性条件的 ABAC 策略。
 */
public class AbacAuthorizationPolicy implements AuthorizationPolicy {

    private final String id;

    private final Set<AuthorizationAction> actions;

    private final List<AttributeCondition> conditions;

    private final AccessEffect effect;

    /**
     * 创建 ABAC 策略。
     */
    public AbacAuthorizationPolicy(
            String id,
            Set<AuthorizationAction> actions,
            List<AttributeCondition> conditions,
            AccessEffect effect) {
        this.id = Objects.requireNonNullElse(id, "abac");
        this.actions = copyActions(actions);
        this.conditions = copyConditions(conditions);
        this.effect = effect == null ? AccessEffect.ALLOW : effect;
    }

    /**
     * 创建允许策略。
     */
    public static AbacAuthorizationPolicy allow(
            String id,
            Set<AuthorizationAction> actions,
            List<AttributeCondition> conditions) {
        return new AbacAuthorizationPolicy(id, actions, conditions, AccessEffect.ALLOW);
    }

    /**
     * 创建拒绝策略。
     */
    public static AbacAuthorizationPolicy deny(
            String id,
            Set<AuthorizationAction> actions,
            List<AttributeCondition> conditions) {
        return new AbacAuthorizationPolicy(id, actions, conditions, AccessEffect.DENY);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        if (request == null || actions.stream().noneMatch(action -> action.matches(request.action()))) {
            return AuthorizationDecision.abstain("action not matched");
        }
        boolean matched = conditions.stream().allMatch(condition -> condition.matches(request));
        if (!matched) {
            return AuthorizationDecision.abstain("attribute conditions not matched");
        }
        if (effect == AccessEffect.DENY) {
            return AuthorizationDecision.deny(id, "attribute policy denied");
        }
        return AuthorizationDecision.allow(id, "attribute policy allowed");
    }

    private Set<AuthorizationAction> copyActions(Set<AuthorizationAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return Set.of(AuthorizationAction.ANY);
        }
        Set<AuthorizationAction> copied = actions.stream()
                .filter(Objects::nonNull)
                .filter(action -> !action.code().isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return copied.isEmpty() ? Set.of(AuthorizationAction.ANY) : copied;
    }

    private List<AttributeCondition> copyConditions(List<AttributeCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        return conditions.stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
