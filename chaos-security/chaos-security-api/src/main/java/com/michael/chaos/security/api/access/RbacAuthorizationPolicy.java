package com.michael.chaos.security.api.access;

import java.util.Objects;
import java.util.Set;

/**
 * 基于角色和权限编码的 RBAC 策略。
 *
 * <p>仅在命中 admin 角色或权限编码时返回 ALLOW；未命中时返回 ABSTAIN，
 * 由 {@link DefaultAuthorizationManager} 在全部策略都未放行时统一拒绝。</p>
 */
public class RbacAuthorizationPolicy implements AuthorizationPolicy {

    private final String id;

    private final Set<String> adminRoles;

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
        this.id = Objects.requireNonNullElse(id, "rbac");
        this.adminRoles = AccessCollections.copyStrings(adminRoles);
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
        if (subject.roles().stream().anyMatch(adminRoles::contains)) {
            return AuthorizationDecision.allow(id, "matched admin role");
        }
        if (subject.permissions().contains(request.action())) {
            return AuthorizationDecision.allow(id, "matched permission");
        }
        return AuthorizationDecision.abstain("missing permission");
    }
}
