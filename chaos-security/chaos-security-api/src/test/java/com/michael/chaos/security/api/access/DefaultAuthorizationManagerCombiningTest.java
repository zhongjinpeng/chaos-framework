package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 多策略合并算法测试。
 */
class DefaultAuthorizationManagerCombiningTest {

    private static final AuthorizationRequest REQUEST =
            AuthorizationRequest.of(AccessSubject.ANONYMOUS, "order:read");

    /**
     * 拒绝优先：先出现 ALLOW 也会被后面的 DENY 覆盖。
     */
    @Test
    void denyOverridesShouldPreferDeny() {
        AuthorizationManager manager = new DefaultAuthorizationManager(
                List.of(allow("p1"), deny("p2")),
                PolicyCombiningAlgorithm.DENY_OVERRIDES);

        AuthorizationDecision decision = manager.decide(REQUEST);

        assertThat(decision.effect()).isEqualTo(AccessEffect.DENY);
        assertThat(decision.policyId()).isEqualTo("p2");
    }

    /**
     * 允许优先：先出现 DENY 也会被后面的 ALLOW 覆盖。
     */
    @Test
    void allowOverridesShouldPreferAllow() {
        AuthorizationManager manager = new DefaultAuthorizationManager(
                List.of(deny("p1"), allow("p2")),
                PolicyCombiningAlgorithm.ALLOW_OVERRIDES);

        AuthorizationDecision decision = manager.decide(REQUEST);

        assertThat(decision.effect()).isEqualTo(AccessEffect.ALLOW);
        assertThat(decision.policyId()).isEqualTo("p2");
    }

    /**
     * 允许优先在没有 ALLOW 时应返回第一个 DENY。
     */
    @Test
    void allowOverridesShouldFallBackToFirstDeny() {
        AuthorizationManager manager = new DefaultAuthorizationManager(
                List.of(abstain("p0"), deny("p1"), deny("p2")),
                PolicyCombiningAlgorithm.ALLOW_OVERRIDES);

        assertThat(manager.decide(REQUEST).policyId()).isEqualTo("p1");
    }

    /**
     * 首个适用：跳过 ABSTAIN，取第一个明确决策。
     */
    @Test
    void firstApplicableShouldTakeFirstNonAbstain() {
        AuthorizationManager manager = new DefaultAuthorizationManager(
                List.of(abstain("p0"), deny("p1"), allow("p2")),
                PolicyCombiningAlgorithm.FIRST_APPLICABLE);

        assertThat(manager.decide(REQUEST).policyId()).isEqualTo("p1");
    }

    /**
     * 全部弃权时默认拒绝。
     */
    @Test
    void shouldDenyWhenAllPoliciesAbstain() {
        AuthorizationManager manager = new DefaultAuthorizationManager(
                List.of(abstain("p0")),
                PolicyCombiningAlgorithm.FIRST_APPLICABLE);

        AuthorizationDecision decision = manager.decide(REQUEST);

        assertThat(decision.effect()).isEqualTo(AccessEffect.DENY);
        assertThat(decision.policyId()).isEqualTo("default");
    }

    /**
     * 单参数构造器保持拒绝优先的历史行为，并容忍 null 策略。
     */
    @Test
    void legacyConstructorShouldKeepDenyOverrides() {
        List<AuthorizationPolicy> policies = new ArrayList<>();
        policies.add(allow("p1"));
        policies.add(null);
        policies.add(deny("p2"));

        assertThat(new DefaultAuthorizationManager(policies).decide(REQUEST).effect()).isEqualTo(AccessEffect.DENY);
        assertThat(new DefaultAuthorizationManager((List<AuthorizationPolicy>) null).decide(REQUEST).effect())
                .isEqualTo(AccessEffect.DENY);
    }

    /**
     * 动态策略来源应在每次决策时重新读取策略。
     */
    @Test
    void shouldReadPoliciesFromSourceOnEachDecision() {
        List<AuthorizationPolicy> policies = new ArrayList<>();
        AuthorizationManager manager = new DefaultAuthorizationManager(
                () -> List.copyOf(policies),
                PolicyCombiningAlgorithm.DENY_OVERRIDES);

        assertThat(manager.isAllowed(REQUEST)).isFalse();

        policies.add(allow("p1"));

        assertThat(manager.isAllowed(REQUEST)).isTrue();
    }

    private AuthorizationPolicy allow(String id) {
        return policy(id, AuthorizationDecision.allow(id, "allow"));
    }

    private AuthorizationPolicy deny(String id) {
        return policy(id, AuthorizationDecision.deny(id, "deny"));
    }

    private AuthorizationPolicy abstain(String id) {
        return policy(id, AuthorizationDecision.abstain("abstain"));
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
