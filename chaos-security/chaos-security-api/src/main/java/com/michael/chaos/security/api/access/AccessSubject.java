package com.michael.chaos.security.api.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 访问主体。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param tenantId 租户 ID
 * @param roles 角色集合
 * @param permissions 权限集合
 * @param attributes 主体属性
 */
public record AccessSubject(
        String userId,
        String username,
        String tenantId,
        Set<String> roles,
        Set<String> permissions,
        Map<String, Object> attributes
) {

    public static final AccessSubject ANONYMOUS = new AccessSubject("", "", "", Set.of(), Set.of(), Map.of());

    /**
     * 规范化主体字段。
     */
    public AccessSubject {
        userId = Objects.requireNonNullElse(userId, "").trim();
        username = Objects.requireNonNullElse(username, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        roles = AccessCollections.copyStrings(roles);
        permissions = AccessCollections.copyStrings(permissions);
        attributes = AccessCollections.copyAttributes(attributes);
    }

    /**
     * 从 LoginUser 创建访问主体。
     */
    public static AccessSubject from(LoginUser user) {
        if (user == null) {
            return ANONYMOUS;
        }
        return new AccessSubject(
                user.userId(),
                user.username(),
                user.tenantId(),
                user.roles(),
                user.permissions(),
                Map.of()
        );
    }

    /**
     * 是否匿名主体。
     */
    public boolean anonymous() {
        return userId.isBlank() && username.isBlank();
    }
}
