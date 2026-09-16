package com.michael.chaos.security.permission;

import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Optional;

/**
 * 默认 RBAC 权限校验实现。
 */
public class DefaultPermissionCheckService implements PermissionCheckService {

    private final PermissionAuthorizationService authorizationService;

    /**
     * 创建默认权限校验服务。
     */
    public DefaultPermissionCheckService(PermissionAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * 已登录用户拥有权限或 admin 角色时放行。
     */
    @Override
    public boolean hasPermission(Optional<LoginUser> user, String permission) {
        return authorizationService.hasPermission(user, permission);
    }
}
