package com.michael.chaos.security.api.access;

import java.util.Map;
import java.util.Objects;

/**
 * 通用授权请求，覆盖 RBAC 和 ABAC 常见决策输入。
 *
 * @param subject 访问主体
 * @param action 动作编码
 * @param resource 资源信息
 * @param environment 环境属性
 */
public record AuthorizationRequest(
        AccessSubject subject,
        String action,
        AuthorizationResource resource,
        Map<String, Object> environment
) {

    /**
     * 规范化授权请求。
     */
    public AuthorizationRequest {
        subject = subject == null ? AccessSubject.ANONYMOUS : subject;
        action = Objects.requireNonNullElse(action, "").trim();
        resource = resource == null ? AuthorizationResource.NONE : resource;
        environment = AccessCollections.copyAttributes(environment);
    }

    /**
     * 创建授权请求。
     */
    public static AuthorizationRequest of(AccessSubject subject, String action) {
        return new AuthorizationRequest(subject, action, AuthorizationResource.NONE, Map.of());
    }

    /**
     * 创建授权请求。
     */
    public static AuthorizationRequest of(AccessSubject subject, String action, AuthorizationResource resource) {
        return new AuthorizationRequest(subject, action, resource, Map.of());
    }

    /**
     * 创建授权请求。
     */
    public static AuthorizationRequest of(
            AccessSubject subject,
            String action,
            AuthorizationResource resource,
            Map<String, Object> environment) {
        return new AuthorizationRequest(subject, action, resource, environment);
    }
}
