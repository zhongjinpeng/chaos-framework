package com.michael.chaos.security.auth;

import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.oauth2.LoginUserOpaqueTokenPrincipal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文访问辅助类。
 */
public final class SecurityUtils {

    private static volatile PermissionAuthorizationService permissionAuthorizationService;

    private SecurityUtils() {
    }

    /**
     * 获取当前已认证用户。
     */
    public static Optional<LoginUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return Optional.of(loginUser);
        }
        if (principal instanceof LoginUserOpaqueTokenPrincipal opaqueTokenPrincipal) {
            return Optional.of(opaqueTokenPrincipal.loginUser());
        }
        return Optional.empty();
    }

    /**
     * 获取当前用户 ID；未认证时返回空字符串。
     */
    public static String userId() {
        return currentUser().map(LoginUser::userId).orElse("");
    }

    /**
     * 获取当前租户 ID；未认证时返回空字符串。
     */
    public static String tenantId() {
        return currentUser().map(LoginUser::tenantId).orElse("");
    }

    /**
     * 判断当前用户是否拥有指定权限。
     *
     * <p>存在 {@link PermissionAuthorizationService} 时委托给统一授权管理器，保证 adminRoles、ABAC DENY 策略
     * 与 {@code @Permission} 注解的判定结果一致；未初始化（非 Spring 环境、单元测试）时退化为只检查用户权限列表。</p>
     */
    public static boolean hasPermission(String permission) {
        PermissionAuthorizationService service = permissionAuthorizationService;
        if (service != null) {
            return service.hasPermission(currentUser(), permission);
        }
        return currentUser().map(user -> user.hasPermission(permission)).orElse(false);
    }

    /**
     * 注册统一权限授权服务，由自动装配在启动时调用。
     *
     * @param service 权限授权服务；传入 {@code null} 表示恢复为本地权限列表判断
     */
    public static void setPermissionAuthorizationService(PermissionAuthorizationService service) {
        permissionAuthorizationService = service;
    }
}
