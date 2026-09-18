package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 授权决策服务装配构造器测试。
 */
class AuthorizationManagerBuilderTest {

    /**
     * 默认拼装：RBAC 放行权限编码，配置策略里的 DENY 能否决 RBAC。
     */
    @Test
    void configuredDenyPolicyShouldVetoRbacAllow() {
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .policyDefinitions(
                        List.of(PolicyDefinition.deny(
                                "deny-external",
                                Set.of("order:read"),
                                List.of(ConditionDefinition.of(
                                        "environment.network",
                                        AttributeOperator.EQ,
                                        Set.of("external"))))),
                        "test.policies")
                .build();
        AccessSubject subject = subject(Set.of("user"), Set.of("order:read"));

        assertThat(manager.isAllowed(AuthorizationRequest.of(subject, "order:read"))).isTrue();
        assertThat(manager.decide(AuthorizationRequest.of(
                subject,
                "order:read",
                AuthorizationResource.NONE,
                Map.of("network", "external"))).policyId()).isEqualTo("deny-external");
    }

    /**
     * 角色继承、通配权限、管理员角色都按配置生效。
     */
    @Test
    void shouldApplyRbacOptions() {
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .adminRoles(List.of("root"))
                .roleHierarchy(Map.of("owner", List.of("root")))
                .wildcardPermissionEnabled(true)
                .build();

        assertThat(manager.isAllowed(AuthorizationRequest.of(subject(Set.of("owner"), Set.of()), "order:delete")))
                .isTrue();
        assertThat(manager.isAllowed(AuthorizationRequest.of(subject(Set.of("user"), Set.of("order:*")), "order:read")))
                .isTrue();
        assertThat(manager.isAllowed(AuthorizationRequest.of(subject(Set.of("user"), Set.of()), "order:read")))
                .isFalse();
    }

    /**
     * 关闭通配后 order:* 不再覆盖 order:read。
     */
    @Test
    void shouldDisableWildcardPermission() {
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .wildcardPermissionEnabled(false)
                .build();

        assertThat(manager.isAllowed(AuthorizationRequest.of(subject(Set.of("user"), Set.of("order:*")), "order:read")))
                .isFalse();
    }

    /**
     * 动态策略来源应参与决策，且每次决策重新读取。
     */
    @Test
    void shouldIncludeDynamicPolicySource() {
        List<AuthorizationPolicy> dynamic = new java.util.ArrayList<>();
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .policySource(() -> List.copyOf(dynamic))
                .build();
        AccessSubject subject = subject(Set.of("user"), Set.of("order:read"));

        assertThat(manager.isAllowed(AuthorizationRequest.of(subject, "order:read"))).isTrue();

        dynamic.add(new AuthorizationPolicy() {

            @Override
            public String id() {
                return "dynamic-deny";
            }

            @Override
            public AuthorizationDecision decide(AuthorizationRequest request) {
                return AuthorizationDecision.deny(id(), "dynamic");
            }
        });

        assertThat(manager.decide(AuthorizationRequest.of(subject, "order:read")).policyId())
                .isEqualTo("dynamic-deny");
    }

    /**
     * 直接注册的策略也参与决策。
     */
    @Test
    void shouldIncludeProvidedPolicies() {
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .policies(List.of(new AuthorizationPolicy() {

                    @Override
                    public String id() {
                        return "always-deny";
                    }

                    @Override
                    public AuthorizationDecision decide(AuthorizationRequest request) {
                        return AuthorizationDecision.deny(id(), "test");
                    }
                }))
                .build();

        assertThat(manager.decide(AuthorizationRequest.of(subject(Set.of("admin"), Set.of()), "order:read"))
                .policyId()).isEqualTo("always-deny");
    }

    /**
     * 策略定义非法时在装配期就失败，并带上配置路径。
     */
    @Test
    void shouldFailFastOnInvalidPolicyDefinition() {
        assertThatThrownBy(() -> AuthorizationManagerBuilder.create()
                .policyDefinitions(
                        List.of(PolicyDefinition.allow(
                                "bad",
                                Set.of("order:read"),
                                List.of(ConditionDefinition.of("subject.tenantId", AttributeOperator.IN, Set.of())))),
                        "chaos.gateway.access.policies"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("chaos.gateway.access.policies[0]");
    }

    private AccessSubject subject(Set<String> roles, Set<String> permissions) {
        return new AccessSubject("1001", "alice", "tenant-a", roles, permissions, Map.of());
    }
}
