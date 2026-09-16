package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Objects;

/**
 * 默认授权决策服务。
 *
 * <p>策略顺序执行；任一策略 DENY 直接拒绝，任一策略 ALLOW 记录允许，全部 ABSTAIN 时默认拒绝。</p>
 */
public class DefaultAuthorizationManager implements AuthorizationManager {

    private final List<AuthorizationPolicy> policies;

    /**
     * 创建默认授权决策服务。
     */
    public DefaultAuthorizationManager(List<AuthorizationPolicy> policies) {
        this.policies = policies == null
                ? List.of()
                : policies.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        AuthorizationDecision allowDecision = null;
        for (AuthorizationPolicy policy : policies) {
            AuthorizationDecision decision = policy.decide(request);
            if (decision == null) {
                continue;
            }
            if (decision.effect() == AccessEffect.DENY) {
                return decision;
            }
            if (decision.effect() == AccessEffect.ALLOW && allowDecision == null) {
                allowDecision = decision;
            }
        }
        if (allowDecision != null) {
            return allowDecision;
        }
        return AuthorizationDecision.deny("default", "no authorization policy allowed the request");
    }
}
