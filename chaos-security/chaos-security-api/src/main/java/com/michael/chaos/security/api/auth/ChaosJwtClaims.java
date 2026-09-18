package com.michael.chaos.security.api.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chaos JWT claim 名称。
 *
 * <p>授权服务器和资源服务器共享同一套 claim 常量，避免字符串散落导致 token 解析不一致。</p>
 */
public final class ChaosJwtClaims {

    /**
     * 用户 ID。
     */
    public static final String USER_ID = "userId";

    /**
     * 用户名。
     */
    public static final String USERNAME = "username";

    /**
     * 租户 ID。
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 角色编码集合。
     */
    public static final String ROLES = "roles";

    /**
     * 权限编码集合。
     */
    public static final String PERMISSIONS = "permissions";

    /**
     * OAuth2 客户端 ID。
     */
    public static final String CLIENT_ID = "clientId";

    /**
     * OAuth2 grant_type。
     */
    public static final String GRANT_TYPE = "grantType";

    /**
     * 登录设备 ID。
     */
    public static final String DEVICE_ID = "deviceId";

    /**
     * OpenID Connect 常用用户名 claim。
     */
    public static final String PREFERRED_USERNAME = "preferred_username";

    private ChaosJwtClaims() {
    }

    /**
     * 把角色 / 权限 claim 统一转换为不可变集合。
     *
     * <p>授权中心写入的 roles、permissions 可能是集合，也可能是逗号分隔的字符串；
     * 资源服务器与网关必须用同一套解析规则，否则同一个 token 在两边的权限集合会不一致。</p>
     *
     * @param claim claim 原始值，可为 null
     */
    public static Set<String> asStringSet(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (claim instanceof String value && !value.isBlank()) {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
