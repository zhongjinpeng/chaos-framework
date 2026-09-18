package com.michael.chaos.security.api.access;

import java.util.List;

/**
 * 多策略决策合并算法。
 *
 * <p>每个常量自带合并实现，调用方只调用 {@link #combine}，不需要判断具体是哪种算法。</p>
 */
public enum PolicyCombiningAlgorithm {
    /**
     * 拒绝优先：任一策略 DENY 立即拒绝，否则取第一个 ALLOW。
     */
    DENY_OVERRIDES(PolicyCombiners::denyOverrides),
    /**
     * 允许优先：任一策略 ALLOW 即放行，否则取第一个 DENY。
     */
    ALLOW_OVERRIDES(PolicyCombiners::allowOverrides),
    /**
     * 首个适用策略生效：取第一个非 ABSTAIN 的决策。
     */
    FIRST_APPLICABLE(PolicyCombiners::firstApplicable);

    private final Combiner combiner;

    PolicyCombiningAlgorithm(Combiner combiner) {
        this.combiner = combiner;
    }

    /**
     * 按本算法合并策略决策；全部策略弃权时返回 ABSTAIN。
     */
    public AuthorizationDecision combine(List<AuthorizationPolicy> policies, AuthorizationRequest request) {
        return combiner.combine(policies == null ? List.of() : policies, request);
    }

    /**
     * 合并实现。
     */
    @FunctionalInterface
    private interface Combiner {

        AuthorizationDecision combine(List<AuthorizationPolicy> policies, AuthorizationRequest request);
    }
}
