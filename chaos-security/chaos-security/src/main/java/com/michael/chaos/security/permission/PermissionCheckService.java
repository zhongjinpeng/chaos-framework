package com.michael.chaos.security.permission;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Optional;

/**
 * 方法权限校验服务。
 */
public interface PermissionCheckService {

    /**
     * 判断用户是否拥有指定权限。
     */
    boolean hasPermission(Optional<LoginUser> user, String permission);
}
