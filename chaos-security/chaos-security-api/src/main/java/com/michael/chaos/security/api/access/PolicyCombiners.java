package com.michael.chaos.security.api.access;

import java.util.List;

/**
 * {@link PolicyCombiningAlgorithm} 各常量的合并实现。
 *
 * <p>三种算法都在"全部策略弃权"时返回 ABSTAIN，是否把弃权当作拒绝由调用方决定：
 * {@link DefaultAuthorizationManager} 作为顶层决策转成默认拒绝，
 * {@link CompositeAuthorizationPolicy} 作为一条策略继续弃权。</p>
 */
final class PolicyCombiners {

    private PolicyCombiners() {
    }

    /**
     * 拒绝优先：任一策略 DENY 立即拒绝，否则取第一个 ALLOW。
     */
    static AuthorizationDecision denyOverrides(List<AuthorizationPolicy> policies, AuthorizationRequest request) {
        AuthorizationDecision allowDecision = null;
        for (AuthorizationDecision decision : applicableDecisions(policies, request)) {
            if (decision.effect() == AccessEffect.DENY) {
                return decision;
            }
            allowDecision = allowDecision == null ? decision : allowDecision;
        }
        return allowDecision == null ? AuthorizationDecision.abstain("no applicable policy") : allowDecision;
    }

    /**
     * 允许优先：任一策略 ALLOW 立即放行，否则取第一个 DENY。
     */
    static AuthorizationDecision allowOverrides(List<AuthorizationPolicy> policies, AuthorizationRequest request) {
        AuthorizationDecision denyDecision = null;
        for (AuthorizationDecision decision : applicableDecisions(policies, request)) {
            if (decision.effect() == AccessEffect.ALLOW) {
                return decision;
            }
            denyDecision = denyDecision == null ? decision : denyDecision;
        }
        return denyDecision == null ? AuthorizationDecision.abstain("no applicable policy") : denyDecision;
    }

    /**
     * 首个适用策略生效：取第一个非 ABSTAIN 的决策。
     */
    static AuthorizationDecision firstApplicable(List<AuthorizationPolicy> policies, AuthorizationRequest request) {
        for (AuthorizationDecision decision : applicableDecisions(policies, request)) {
            return decision;
        }
        return AuthorizationDecision.abstain("no applicable policy");
    }

    /**
     * 惰性执行策略并跳过弃权结果：拒绝优先命中 DENY 时，后面的策略不会被执行。
     */
    private static Iterable<AuthorizationDecision> applicableDecisions(
            List<AuthorizationPolicy> policies,
            AuthorizationRequest request) {
        return () -> policies.stream()
                .map(policy -> policy.decide(request))
                .filter(decision -> decision != null && decision.effect() != AccessEffect.ABSTAIN)
                .iterator();
    }
}
