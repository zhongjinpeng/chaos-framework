package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 组合策略测试。
 */
class CompositeAuthorizationPolicyTest {

    private static final AuthorizationRequest REQUEST =
            AuthorizationRequest.of(AccessSubject.ANONYMOUS, "order:read");

    /**
     * 组内没有策略时应弃权，不能变成拒绝而把上层的 RBAC 放行覆盖掉。
     */
    @Test
    void shouldAbstainWhenEmpty() {
        CompositeAuthorizationPolicy policy = new CompositeAuthorizationPolicy("configured", List.of(), null);

        assertThat(policy.decide(REQUEST).effect()).isEqualTo(AccessEffect.ABSTAIN);
        assertThat(policy.id()).isEqualTo("configured");
        assertThat(policy.size()).isZero();
    }

    /**
     * 组内全部弃权时整体弃权。
     */
    @Test
    void shouldAbstainWhenAllInnerPoliciesAbstain() {
        CompositeAuthorizationPolicy policy = new CompositeAuthorizationPolicy(
                "configured",
                List.of(policy("p1", AuthorizationDecision.abstain("n/a"))),
                null);

        assertThat(policy.decide(REQUEST).effect()).isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * 组内按指定算法合并，并保留命中策略自己的 ID。
     */
    @Test
    void shouldCombineInnerPoliciesWithAlgorithm() {
        List<AuthorizationPolicy> policies = List.of(
                policy("p1", AuthorizationDecision.deny("p1", "deny")),
                policy("p2", AuthorizationDecision.allow("p2", "allow")));

        assertThat(new CompositeAuthorizationPolicy("c", policies, PolicyCombiningAlgorithm.DENY_OVERRIDES)
                .decide(REQUEST).policyId()).isEqualTo("p1");
        assertThat(new CompositeAuthorizationPolicy("c", policies, PolicyCombiningAlgorithm.ALLOW_OVERRIDES)
                .decide(REQUEST).policyId()).isEqualTo("p2");
        assertThat(new CompositeAuthorizationPolicy("c", policies, PolicyCombiningAlgorithm.FIRST_APPLICABLE)
                .decide(REQUEST).policyId()).isEqualTo("p1");
    }

    /**
     * 组合策略的 DENY 应能否决上层 RBAC 的放行。
     */
    @Test
    void shouldVetoOuterAllow() {
        AuthorizationManager manager = new DefaultAuthorizationManager(List.of(
                policy("rbac", AuthorizationDecision.allow("rbac", "matched permission")),
                new CompositeAuthorizationPolicy(
                        "configured",
                        List.of(policy("deny-external", AuthorizationDecision.deny("deny-external", "external"))),
                        PolicyCombiningAlgorithm.ALLOW_OVERRIDES)));

        assertThat(manager.decide(REQUEST).effect()).isEqualTo(AccessEffect.DENY);
    }

    private AuthorizationPolicy policy(String id, AuthorizationDecision decision) {
        return new AuthorizationPolicy() {

            @Override
            public String id() {
                return id;
            }

            @Override
            public AuthorizationDecision decide(AuthorizationRequest request) {
                return decision;
            }
        };
    }
}
