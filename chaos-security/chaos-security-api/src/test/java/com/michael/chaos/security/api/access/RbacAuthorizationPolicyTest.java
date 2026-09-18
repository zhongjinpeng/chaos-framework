package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * RBAC 策略的角色继承与权限通配测试。
 */
class RbacAuthorizationPolicyTest {

    /**
     * 角色继承展开后命中 admin 角色应放行。
     */
    @Test
    void shouldAllowWhenInheritedRoleIsAdmin() {
        RoleHierarchy hierarchy = new MapRoleHierarchy(Map.of("root", List.of("admin")));
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy("rbac", Set.of("admin"), hierarchy, false);
        AccessSubject subject = subject(Set.of("root"), Set.of());

        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:delete")).allowed()).isTrue();
    }

    /**
     * 开启通配后权限编码应支持 {@code order:*}。
     */
    @Test
    void shouldAllowWildcardPermissionWhenEnabled() {
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy("rbac", Set.of("admin"), null, true);
        AccessSubject subject = subject(Set.of("user"), Set.of("order:*"));

        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:read")).allowed()).isTrue();
        assertThat(policy.decide(AuthorizationRequest.of(subject, "payment:read")).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * 未开启通配时通配权限不生效。
     */
    @Test
    void shouldIgnoreWildcardPermissionWhenDisabled() {
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy("rbac", Set.of("admin"), null, false);
        AccessSubject subject = subject(Set.of("user"), Set.of("order:*"));

        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:read")).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * 旧构造器行为保持不变：精确权限命中、匿名与缺失动作弃权。
     */
    @Test
    void legacyConstructorShouldKeepBehaviour() {
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy();
        AccessSubject subject = subject(Set.of("user"), Set.of("order:read"));

        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:read")).allowed()).isTrue();
        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:*")).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
        assertThat(policy.decide(AuthorizationRequest.of(AccessSubject.ANONYMOUS, "order:read")).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
        assertThat(policy.decide(AuthorizationRequest.of(subject, " ")).effect()).isEqualTo(AccessEffect.ABSTAIN);
        assertThat(policy.decide(null).effect()).isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * 决策原因应能区分精确命中与通配命中。
     */
    @Test
    void shouldReportMatchReason() {
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy("rbac", Set.of("admin"), null, true);

        assertThat(policy.decide(AuthorizationRequest.of(subject(Set.of("admin"), Set.of()), "order:read")).reason())
                .isEqualTo("matched admin role");
        assertThat(policy.decide(AuthorizationRequest.of(subject(Set.of(), Set.of("order:read")), "order:read")).reason())
                .isEqualTo("matched permission");
        assertThat(policy.decide(AuthorizationRequest.of(subject(Set.of(), Set.of("order:*")), "order:read")).reason())
                .isEqualTo("matched wildcard permission");
    }

    private AccessSubject subject(Set<String> roles, Set<String> permissions) {
        return AccessSubject.from(new LoginUser("1001", "alice", "tenant-a", roles, permissions));
    }
}
