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
     * 作为认证主体随令牌一起被 JDK 序列化写入 Redis，必须钉死 UID，理由同
     * {@code LoginUserAuthenticationToken}：record 的 UID 同样由组件推导，加一个字段就全废。
     */
    private static final long serialVersionUID = 1L;

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
