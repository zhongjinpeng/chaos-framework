package com.michael.chaos.gateway.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网关粗粒度鉴权规则：哪些路径、哪些方法需要哪个权限。
 *
 * <p>网关只做"进不进得来"的粗粒度判断，字段级、数据级授权仍由下游服务用 {@code @RequireAccess} 完成。</p>
 */
public class AccessRuleProperties {

    /**
     * Ant 风格路径表达式，例如 /api/orders/**。
     */
    private String path;

    /**
     * 适用的 HTTP 方法，留空表示全部方法。
     */
    private List<String> methods = List.of();

    /**
     * 命中后要求的权限编码，同时作为 ABAC 策略的动作，例如 order:read。
     */
    private String action;

    /**
     * 资源类型，写入 ABAC 请求的 resource.type，便于策略按资源区分。
     */
    private String resourceType;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getMethods() {
        return List.copyOf(methods);
    }

    public void setMethods(List<String> methods) {
        this.methods = methods == null ? List.of() : List.copyOf(methods);
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * 规范化后的 HTTP 方法集合，留空表示不限方法。
     */
    public Set<String> normalizedMethods() {
        return methods.stream()
                .filter(method -> method != null && !method.isBlank())
                .map(method -> method.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
