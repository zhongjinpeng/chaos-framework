package com.michael.chaos.security.api.access;

import java.util.LinkedHashMap;
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

    /**
     * 创建授权请求构造器。
     */
    public static Builder builder(AccessSubject subject, String action) {
        return new Builder(subject, action);
    }

    /**
     * 授权请求构造器：环境属性可以逐个写入，也可以交给
     * {@link AuthorizationContextContributor} 填充。
     */
    public static final class Builder {

        private final AccessSubject subject;

        private final String action;

        private AuthorizationResource resource = AuthorizationResource.NONE;

        private final Map<String, Object> environment = new LinkedHashMap<>();

        private Builder(AccessSubject subject, String action) {
            this.subject = subject;
            this.action = action;
        }

        /**
         * 设置资源。
         */
        public Builder resource(AuthorizationResource resource) {
            this.resource = resource;
            return this;
        }

        /**
         * 写入一个环境属性，值为 null 时忽略。
         */
        public Builder environmentAttribute(String name, Object value) {
            if (name != null && !name.isBlank() && value != null) {
                environment.put(name.trim(), value);
            }
            return this;
        }

        /**
         * 用环境属性贡献者填充环境属性。
         */
        public Builder contribute(AuthorizationContextContributor contributor) {
            if (contributor != null) {
                contributor.contribute(environment);
            }
            return this;
        }

        /**
         * 构造授权请求。
         */
        public AuthorizationRequest build() {
            return new AuthorizationRequest(subject, action, resource, environment);
        }
    }
}
