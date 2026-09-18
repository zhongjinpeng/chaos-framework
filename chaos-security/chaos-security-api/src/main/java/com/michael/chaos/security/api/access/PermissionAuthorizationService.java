package com.michael.chaos.security.api.access;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 面向权限编码的授权服务，兼容现有 @Permission 使用方式。
 */
public class PermissionAuthorizationService {

    private final AuthorizationManager authorizationManager;

    private final Function<LoginUser, AccessSubject> subjectFactory;

    /**
     * 创建权限授权服务，主体直接来自 LoginUser。
     */
    public PermissionAuthorizationService(AuthorizationManager authorizationManager) {
        this(authorizationManager, AccessSubject::from);
    }

    /**
     * 创建权限授权服务，并指定访问主体的构造方式。
     *
     * <p>JWT 只携带角色、权限需要在服务端补全时，通过 subjectFactory 接入权限解析逻辑。</p>
     */
    public PermissionAuthorizationService(
            AuthorizationManager authorizationManager,
            Function<LoginUser, AccessSubject> subjectFactory) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager must not be null");
        this.subjectFactory = subjectFactory == null ? AccessSubject::from : subjectFactory;
    }

    /**
     * 判断用户是否具备权限。
     */
    public boolean hasPermission(Optional<LoginUser> user, String permission) {
        AccessSubject subject = subject(user);
        return authorizationManager.isAllowed(AuthorizationRequest.of(subject, permission));
    }

    /**
     * 构造访问主体，匿名时返回 {@link AccessSubject#ANONYMOUS}。
     */
    public AccessSubject subject(Optional<LoginUser> user) {
        return user.map(subjectFactory)
                .map(subject -> subject == null ? AccessSubject.ANONYMOUS : subject)
                .orElse(AccessSubject.ANONYMOUS);
    }
}
