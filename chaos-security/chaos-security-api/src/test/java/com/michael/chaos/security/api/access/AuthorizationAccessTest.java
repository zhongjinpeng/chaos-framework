package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 通用 RBAC/ABAC 授权模型测试。
 */
class AuthorizationAccessTest {

    /**
     * RBAC 应支持权限编码和 admin 角色。
     */
    @Test
    void rbacPolicyShouldAllowPermissionOrAdminRole() {
        RbacAuthorizationPolicy policy = new RbacAuthorizationPolicy();
        AccessSubject user = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of("user"),
                Set.of("order:read")
        ));
        AccessSubject admin = AccessSubject.from(new LoginUser(
                "1002",
                "root",
                "tenant-a",
                Set.of("admin"),
                Set.of()
        ));

        assertThat(policy.decide(AuthorizationRequest.of(user, "order:read")).allowed()).isTrue();
        assertThat(policy.decide(AuthorizationRequest.of(admin, "order:delete")).allowed()).isTrue();
        assertThat(policy.decide(AuthorizationRequest.of(user, "order:delete")).effect()).isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * ABAC 应支持主体属性和资源属性比较。
     */
    @Test
    void abacPolicyShouldMatchSubjectAndResourceAttributes() {
        AbacAuthorizationPolicy policy = AbacAuthorizationPolicy.allow(
                "same-tenant",
                Set.of(AuthorizationAction.of("order:read")),
                List.of(AttributeCondition.eq(
                        AttributeReference.subject("tenantId"),
                        AttributeReference.resource("tenantId")
                ))
        );
        AccessSubject subject = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of(),
                Set.of()
        ));
        AuthorizationResource resource = AuthorizationResource.of(
                "order",
                "order-1",
                Map.of("tenantId", "tenant-a")
        );

        AuthorizationDecision decision = policy.decide(AuthorizationRequest.of(subject, "order:read", resource));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyId()).isEqualTo("same-tenant");
    }

    /**
     * ABAC 应支持资源内置字段和环境属性条件。
     */
    @Test
    void abacPolicyShouldMatchResourceBuiltinFieldsAndEnvironmentAttributes() {
        AbacAuthorizationPolicy policy = AbacAuthorizationPolicy.allow(
                "business-hours-order",
                Set.of(AuthorizationAction.of("order:update")),
                List.of(
                        AttributeCondition.eq(AttributeReference.resource("type"), "order"),
                        AttributeCondition.exists(AttributeReference.resource("id")),
                        AttributeCondition.in(AttributeReference.environment("channel"), Set.of("WEB", "APP")),
                        AttributeCondition.notEq(AttributeReference.resource("status"), "FROZEN")
                )
        );
        AccessSubject subject = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of(),
                Set.of()
        ));
        AuthorizationResource resource = AuthorizationResource.of(
                "order",
                "order-1",
                Map.of("status", "NORMAL")
        );
        AuthorizationRequest request = AuthorizationRequest.of(
                subject,
                "order:update",
                resource,
                Map.of("channel", "WEB")
        );

        AuthorizationDecision decision = policy.decide(request);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyId()).isEqualTo("business-hours-order");
    }

    /**
     * RBAC 未命中不应阻断 ABAC 放行。
     */
    @Test
    void authorizationManagerShouldAllowWhenRbacAbstainsAndAbacAllows() {
        AbacAuthorizationPolicy sameTenant = AbacAuthorizationPolicy.allow(
                "same-tenant",
                Set.of(AuthorizationAction.of("order:update")),
                List.of(AttributeCondition.eq(
                        AttributeReference.subject("tenantId"),
                        AttributeReference.resource("tenantId")
                ))
        );
        DefaultAuthorizationManager manager = new DefaultAuthorizationManager(List.of(
                new RbacAuthorizationPolicy(),
                sameTenant
        ));
        AccessSubject subject = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of(),
                Set.of()
        ));
        AuthorizationResource resource = AuthorizationResource.of(
                "order",
                "order-1",
                Map.of("tenantId", "tenant-a")
        );

        AuthorizationDecision decision = manager.decide(AuthorizationRequest.of(subject, "order:update", resource));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.policyId()).isEqualTo("same-tenant");
    }

    /**
     * 组合决策必须 deny 优先，避免 ABAC 拒绝被 RBAC 允许覆盖。
     */
    @Test
    void authorizationManagerShouldPreferDeny() {
        AbacAuthorizationPolicy denyFrozenResource = AbacAuthorizationPolicy.deny(
                "deny-frozen",
                Set.of(AuthorizationAction.ANY),
                List.of(AttributeCondition.eq(AttributeReference.resource("status"), "FROZEN"))
        );
        DefaultAuthorizationManager manager = new DefaultAuthorizationManager(List.of(
                new RbacAuthorizationPolicy(),
                denyFrozenResource
        ));
        AccessSubject subject = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of(),
                Set.of("order:read")
        ));
        AuthorizationResource resource = AuthorizationResource.of(
                "order",
                "order-1",
                Map.of("status", "FROZEN")
        );

        AuthorizationDecision decision = manager.decide(AuthorizationRequest.of(subject, "order:read", resource));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.policyId()).isEqualTo("deny-frozen");
    }

    /**
     * 全部策略弃权时必须默认拒绝，保持 fail-closed。
     */
    @Test
    void authorizationManagerShouldDenyWhenEveryPolicyAbstains() {
        DefaultAuthorizationManager manager = new DefaultAuthorizationManager(List.of(new RbacAuthorizationPolicy()));
        AccessSubject subject = AccessSubject.from(new LoginUser(
                "1001",
                "alice",
                "tenant-a",
                Set.of(),
                Set.of()
        ));

        AuthorizationDecision decision = manager.decide(AuthorizationRequest.of(subject, "order:delete"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.policyId()).isEqualTo("default");
    }

    /**
     * 属性输入应允许空值并规范化空字符串，避免构造条件时 NPE。
     */
    @Test
    void attributeConditionShouldBeNullSafe() {
        AttributeCondition emptyValue = AttributeCondition.eq(AttributeReference.resource("status"), (String) null);
        AttributeCondition missingTenant = AttributeCondition.eq(
                AttributeReference.subject("tenantId"),
                AttributeReference.resource("tenantId")
        );
        AttributeCondition missingOwner = AttributeCondition.notExists(AttributeReference.resource("ownerId"));
        AuthorizationRequest request = AuthorizationRequest.of(
                AccessSubject.ANONYMOUS,
                "order:read",
                AuthorizationResource.of("order", "order-1", Map.of())
        );

        assertThat(emptyValue.matches(request)).isFalse();
        assertThat(missingTenant.matches(request)).isFalse();
        assertThat(missingOwner.matches(request)).isTrue();
    }
}
