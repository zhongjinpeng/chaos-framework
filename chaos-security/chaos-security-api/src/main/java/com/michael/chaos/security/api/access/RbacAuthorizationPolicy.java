package com.michael.chaos.security.api.access;

import java.util.Objects;
import java.util.Set;

/**
 * 基于角色和权限编码的 RBAC 策略。
 *
 * <p>仅在命中 admin 角色或权限编码时返回 ALLOW；未命中时返回 ABSTAIN，
 * 由 {@link DefaultAuthorizationManager} 在全部策略都未放行时统一拒绝。</p>
 *
 * <p>可选能力：{@link RoleHierarchy} 展开继承角色；权限编码通配匹配见 {@link PermissionPatterns}。</p>
 */
public class RbacAuthorizationPolicy implements AuthorizationPolicy {

    private final String id;

    private final Set<String> adminRoles;

    private final RoleHierarchy roleHierarchy;

    private final boolean wildcardPermissionEnabled;

    /**
     * 创建默认 RBAC 策略。
     */
    public RbacAuthorizationPolicy() {
        this("rbac", Set.of("admin"));
    }

    /**
     * 创建 RBAC 策略。
     */
    public RbacAuthorizationPolicy(String id, Set<String> adminRoles) {
        this(id, adminRoles, RoleHierarchy.none(), false);
    }

    /**
     * 创建支持角色继承与权限通配的 RBAC 策略。
     *
     * @param id 策略 ID
     * @param adminRoles 命中即放行的管理员角色
     * @param roleHierarchy 角色继承关系，为 null 时不做展开
     * @param wildcardPermissionEnabled 是否允许主体权限编码使用 {@code *} 通配
     */
    public RbacAuthorizationPolicy(
            String id,
            Set<String> adminRoles,
            RoleHierarchy roleHierarchy,
            boolean wildcardPermissionEnabled) {
        this.id = Objects.requireNonNullElse(id, "rbac");
        this.adminRoles = AccessCollections.copyStrings(adminRoles);
        this.roleHierarchy = roleHierarchy == null ? RoleHierarchy.none() : roleHierarchy;
        this.wildcardPermissionEnabled = wildcardPermissionEnabled;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        if (request == null || request.action().isBlank()) {
            return AuthorizationDecision.abstain("missing action");
        }
        AccessSubject subject = request.subject();
        if (subject.anonymous()) {
            return AuthorizationDecision.abstain("anonymous subject");
        }
        Set<String> roles = roleHierarchy.reachableRoles(subject.roles());
        if (roles.stream().anyMatch(adminRoles::contains)) {
            return AuthorizationDecision.allow(id, "matched admin role");
        }
        if (subject.permissions().contains(request.action())) {
            return AuthorizationDecision.allow(id, "matched permission");
        }
        if (wildcardPermissionEnabled && PermissionPatterns.matchesAny(subject.permissions(), request.action())) {
            return AuthorizationDecision.allow(id, "matched wildcard permission");
        }
        return AuthorizationDecision.abstain("missing permission");
    }
}
