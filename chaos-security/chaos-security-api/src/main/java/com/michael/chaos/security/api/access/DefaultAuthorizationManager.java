package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 默认授权决策服务。
 *
 * <p>策略顺序执行，按 {@link PolicyCombiningAlgorithm} 合并结果；全部策略都未放行时默认拒绝。</p>
 *
 * <p>策略列表可以是固定集合，也可以来自 {@link AuthorizationPolicySource}（动态策略来源）。</p>
 */
public class DefaultAuthorizationManager implements AuthorizationManager {

    private final Supplier<List<AuthorizationPolicy>> policies;

    private final PolicyCombiningAlgorithm combiningAlgorithm;

    /**
     * 创建默认授权决策服务，使用拒绝优先算法。
     */
    public DefaultAuthorizationManager(List<AuthorizationPolicy> policies) {
        this(policies, PolicyCombiningAlgorithm.DENY_OVERRIDES);
    }

    /**
     * 创建指定合并算法的授权决策服务。
     */
    public DefaultAuthorizationManager(
            List<AuthorizationPolicy> policies,
            PolicyCombiningAlgorithm combiningAlgorithm) {
        List<AuthorizationPolicy> copied = copy(policies);
        this.policies = () -> copied;
        this.combiningAlgorithm = defaultAlgorithm(combiningAlgorithm);
    }

    /**
     * 创建从动态策略来源取策略的授权决策服务。
     */
    public DefaultAuthorizationManager(
            AuthorizationPolicySource policySource,
            PolicyCombiningAlgorithm combiningAlgorithm) {
        Objects.requireNonNull(policySource, "policySource must not be null");
        this.policies = () -> copy(policySource.policies());
        this.combiningAlgorithm = defaultAlgorithm(combiningAlgorithm);
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        AuthorizationDecision decision = combiningAlgorithm.combine(policies.get(), request);
        if (decision.effect() == AccessEffect.ABSTAIN) {
            return AuthorizationDecision.deny("default", "no authorization policy allowed the request");
        }
        return decision;
    }

    private static List<AuthorizationPolicy> copy(List<AuthorizationPolicy> policies) {
        return policies == null
                ? List.of()
                : policies.stream().filter(Objects::nonNull).toList();
    }

    private static PolicyCombiningAlgorithm defaultAlgorithm(PolicyCombiningAlgorithm combiningAlgorithm) {
        return combiningAlgorithm == null ? PolicyCombiningAlgorithm.DENY_OVERRIDES : combiningAlgorithm;
    }
}
