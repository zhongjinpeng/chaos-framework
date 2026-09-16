package com.michael.chaos.security.api.access;

import java.util.Map;
import java.util.Objects;

/**
 * 授权资源。
 *
 * @param type 资源类型
 * @param id 资源 ID
 * @param attributes 资源属性
 */
public record AuthorizationResource(String type, String id, Map<String, Object> attributes) {

    public static final AuthorizationResource NONE = new AuthorizationResource("", "", Map.of());

    /**
     * 规范化资源属性。
     */
    public AuthorizationResource {
        type = Objects.requireNonNullElse(type, "").trim();
        id = Objects.requireNonNullElse(id, "").trim();
        attributes = AccessCollections.copyAttributes(attributes);
    }

    /**
     * 创建资源。
     */
    public static AuthorizationResource of(String type, String id) {
        return new AuthorizationResource(type, id, Map.of());
    }

    /**
     * 创建资源。
     */
    public static AuthorizationResource of(String type, String id, Map<String, Object> attributes) {
        return new AuthorizationResource(type, id, attributes);
    }
}
