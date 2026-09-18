package com.michael.chaos.security.api.access;

import java.util.LinkedHashMap;
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

    /**
     * 创建资源构造器。
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    /**
     * 资源构造器：属性逐个写入，避免调用方自己拼 Map。
     */
    public static final class Builder {

        private final String type;

        private String id = "";

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = type;
        }

        /**
         * 设置资源 ID。
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * 写入一个资源属性，值为 null 时忽略。
         */
        public Builder attribute(String name, Object value) {
            if (name != null && !name.isBlank() && value != null) {
                attributes.put(name.trim(), value);
            }
            return this;
        }

        /**
         * 批量写入资源属性。
         */
        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                attributes.forEach(this::attribute);
            }
            return this;
        }

        /**
         * 构造资源；没有任何内容时返回 {@link AuthorizationResource#NONE}。
         */
        public AuthorizationResource build() {
            AuthorizationResource resource = new AuthorizationResource(type, id, attributes);
            if (resource.type().isBlank() && resource.id().isBlank() && resource.attributes().isEmpty()) {
                return NONE;
            }
            return resource;
        }
    }
}
