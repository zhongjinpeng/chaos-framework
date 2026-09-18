package com.michael.chaos.security.access;

import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 访问主体工厂：把登录用户转换为授权决策用的 {@link AccessSubject}。
 *
 * <p>在这里统一接入 {@link PermissionResolver}（服务端补全权限）与 {@link SubjectAttributeResolver}
 * （ABAC 主体属性），使 {@code @Permission}、{@code @RequireAccess} 和 {@code SecurityUtils.hasPermission}
 * 看到同一个主体。</p>
 */
public class AccessSubjectFactory implements Function<LoginUser, AccessSubject> {

    private final PermissionResolver permissionResolver;

    private final List<SubjectAttributeResolver> attributeResolvers;

    /**
     * 创建只补全权限的主体工厂。
     */
    public AccessSubjectFactory(PermissionResolver permissionResolver) {
        this(permissionResolver, List.of());
    }

    /**
     * 创建主体工厂。
     *
     * @param permissionResolver 权限解析器，为 null 时使用令牌里的权限
     * @param attributeResolvers 主体属性解析器，按顺序合并，后者覆盖前者
     */
    public AccessSubjectFactory(
            PermissionResolver permissionResolver,
            List<SubjectAttributeResolver> attributeResolvers) {
        this.permissionResolver = permissionResolver == null ? new ClaimsPermissionResolver() : permissionResolver;
        this.attributeResolvers = attributeResolvers == null
                ? List.of()
                : attributeResolvers.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public AccessSubject apply(LoginUser user) {
        if (user == null) {
            return AccessSubject.ANONYMOUS;
        }
        return AccessSubject.from(user, permissions(user), attributes(user));
    }

    private Set<String> permissions(LoginUser user) {
        Set<String> resolved = permissionResolver.resolve(user);
        return resolved == null ? user.permissions() : resolved;
    }

    private Map<String, Object> attributes(LoginUser user) {
        if (attributeResolvers.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (SubjectAttributeResolver resolver : attributeResolvers) {
            Map<String, Object> resolved = resolver.resolve(user);
            if (resolved != null) {
                attributes.putAll(resolved);
            }
        }
        return attributes;
    }
}
