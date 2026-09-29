package com.chaos.security.auth;

import com.chaos.security.api.auth.LoginUser;
import java.util.Optional;
import java.util.Set;

/**
 * 当前登录用户访问工具。
 *
 * <p>业务代码只需要访问登录用户时使用本类，不要直接操作 {@code SecurityContextHolder}。
 * 认证主体统一由 {@link SecurityUtils} 解析，兼容 JWT 和 opaque token 两种资源服务器模式。</p>
 */
public final class LoginUserUtils {

    private LoginUserUtils() {
    }

    /**
     * 获取当前登录用户。
     *
     * @return 已认证用户；未登录时为空
     */
    public static Optional<LoginUser> currentUser() {
        return SecurityUtils.currentUser();
    }

    /**
     * 获取当前登录用户，未登录时抛出异常。
     *
     * @return 已认证用户
     * @throws IllegalStateException 当前请求没有已认证用户
     */
    public static LoginUser requiredUser() {
        return currentUser().orElseThrow(() ->
                new IllegalStateException("当前请求未认证登录用户"));
    }

    /**
     * 获取当前用户 ID；未登录时返回空字符串。
     */
    public static String userId() {
        return currentUser().map(LoginUser::userId).orElse("");
    }

    /**
     * 获取当前用户名；未登录时返回空字符串。
     */
    public static String username() {
        return currentUser().map(LoginUser::username).orElse("");
    }

    /**
     * 获取当前租户 ID；未登录时返回空字符串。
     */
    public static String tenantId() {
        return currentUser().map(LoginUser::tenantId).orElse("");
    }

    /**
     * 获取当前用户角色；未登录时返回不可变空集合。
     */
    public static Set<String> roles() {
        return currentUser().map(LoginUser::roles).orElseGet(Set::of);
    }

    /**
     * 获取当前用户权限；未登录时返回不可变空集合。
     */
    public static Set<String> permissions() {
        return currentUser().map(LoginUser::permissions).orElseGet(Set::of);
    }
}
