package com.michael.chaos.security.api.auth;

import java.io.Serializable;
import java.util.Set;

/**
 * 已认证用户信息。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param tenantId 当前租户 ID
 * @param roles 用户角色编码集合
 * @param permissions 用户权限编码集合
 */
public record LoginUser(
        String userId,
        String username,
        String tenantId,
        Set<String> roles,
        Set<String> permissions
) implements Serializable {

    /**
     * 规范化角色和权限集合，避免认证主体被外部修改。
     */
    public LoginUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /**
     * 判断当前用户是否拥有指定权限。
     *
     * @param permission 权限编码
     * @return 拥有权限或具备 admin 角色时返回 {@code true}
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission) || roles.contains("admin");
    }
}
