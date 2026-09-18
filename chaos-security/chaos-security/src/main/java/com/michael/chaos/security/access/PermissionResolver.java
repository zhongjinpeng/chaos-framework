package com.michael.chaos.security.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Set;

/**
 * 权限集合解析器。
 *
 * <p>令牌里只带角色、权限需要在服务端按角色展开时实现本接口；默认实现
 * {@link ClaimsPermissionResolver} 直接使用令牌中的权限。</p>
 */
@FunctionalInterface
public interface PermissionResolver {

    /**
     * 解析用户的权限编码集合。
     */
    Set<String> resolve(LoginUser user);
}
