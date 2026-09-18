package com.michael.chaos.security.api.access;

import java.util.Locale;
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
     * 解析 {@code <命名空间>.<属性名>} 形式的属性引用，例如 {@code subject.tenantId}、{@code resource.ownerId}、
     * {@code environment.clientIp}（{@code env} 是 {@code environment} 的简写）。
     *
     * <p>前缀不是已知命名空间时，整个文本作为 ENVIRONMENT 属性名，因此 {@code http.method} 等带点的环境属性名
     * 可以直接书写。</p>
     *
     * @throws IllegalArgumentException 表达式为空时抛出
     */
    public static AttributeReference parse(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("attribute reference must not be blank");
        }
        int separator = normalized.indexOf('.');
        if (separator > 0 && separator < normalized.length() - 1) {
            String prefix = normalized.substring(0, separator).toLowerCase(Locale.ROOT);
            String name = normalized.substring(separator + 1);
            AttributeNamespace namespace = switch (prefix) {
                case "subject" -> AttributeNamespace.SUBJECT;
                case "resource" -> AttributeNamespace.RESOURCE;
                case "environment", "env" -> AttributeNamespace.ENVIRONMENT;
                default -> null;
            };
            if (namespace != null) {
                return new AttributeReference(namespace, name);
            }
        }
        return new AttributeReference(AttributeNamespace.ENVIRONMENT, normalized);
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
