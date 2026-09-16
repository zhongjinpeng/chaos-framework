package com.michael.chaos.security.api.access;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * ABAC 属性引用。
 *
 * @param namespace 属性命名空间
 * @param name 属性名
 */
public record AttributeReference(AttributeNamespace namespace, String name) {

    /**
     * 规范化属性引用。
     */
    public AttributeReference {
        namespace = namespace == null ? AttributeNamespace.ENVIRONMENT : namespace;
        name = Objects.requireNonNullElse(name, "").trim();
    }

    /**
     * 主体属性引用。
     */
    public static AttributeReference subject(String name) {
        return new AttributeReference(AttributeNamespace.SUBJECT, name);
    }

    /**
     * 资源属性引用。
     */
    public static AttributeReference resource(String name) {
        return new AttributeReference(AttributeNamespace.RESOURCE, name);
    }

    /**
     * 环境属性引用。
     */
    public static AttributeReference environment(String name) {
        return new AttributeReference(AttributeNamespace.ENVIRONMENT, name);
    }

    /**
     * 从请求中解析属性值。
     */
    public Optional<Object> resolve(AuthorizationRequest request) {
        if (request == null || name.isBlank()) {
            return Optional.empty();
        }
        return switch (namespace) {
            case SUBJECT -> resolveSubject(request.subject(), name);
            case RESOURCE -> resolveResource(request.resource(), name);
            case ENVIRONMENT -> resolveFromMap(request.environment(), name);
        };
    }

    private Optional<Object> resolveSubject(AccessSubject subject, String attributeName) {
        return switch (attributeName) {
            case "userId" -> optionalString(subject.userId());
            case "username" -> optionalString(subject.username());
            case "tenantId" -> optionalString(subject.tenantId());
            case "roles" -> Optional.of(subject.roles());
            case "permissions" -> Optional.of(subject.permissions());
            default -> resolveFromMap(subject.attributes(), attributeName);
        };
    }

    private Optional<Object> resolveResource(AuthorizationResource resource, String attributeName) {
        return switch (attributeName) {
            case "type" -> optionalString(resource.type());
            case "id" -> optionalString(resource.id());
            default -> resolveFromMap(resource.attributes(), attributeName);
        };
    }

    private Optional<Object> optionalString(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Optional<Object> resolveFromMap(Map<String, Object> attributes, String attributeName) {
        if (attributes == null || !attributes.containsKey(attributeName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributes.get(attributeName));
    }
}
