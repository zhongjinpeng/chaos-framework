package com.michael.chaos.security.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Set;

/**
 * 默认权限解析器：直接使用令牌声明里的权限集合。
 */
public class ClaimsPermissionResolver implements PermissionResolver {

    @Override
    public Set<String> resolve(LoginUser user) {
        return user == null ? Set.of() : user.permissions();
    }
}
