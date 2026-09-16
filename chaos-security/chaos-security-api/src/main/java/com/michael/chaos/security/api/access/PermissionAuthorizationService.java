package com.michael.chaos.security.api.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Objects;
import java.util.Optional;

/**
 * 面向权限编码的授权服务，兼容现有 @Permission 使用方式。
 */
public class PermissionAuthorizationService {

    private final AuthorizationManager authorizationManager;

    /**
     * 创建权限授权服务。
     */
    public PermissionAuthorizationService(AuthorizationManager authorizationManager) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager must not be null");
    }

    /**
     * 判断用户是否具备权限。
     */
    public boolean hasPermission(Optional<LoginUser> user, String permission) {
        AccessSubject subject = user.map(AccessSubject::from).orElse(AccessSubject.ANONYMOUS);
        return authorizationManager.isAllowed(AuthorizationRequest.of(subject, permission));
    }
}
