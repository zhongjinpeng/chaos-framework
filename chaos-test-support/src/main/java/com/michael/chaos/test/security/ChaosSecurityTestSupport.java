package com.michael.chaos.test.security;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 基于 {@link LoginUser} 的 Spring Security 测试辅助。
 *
 * <p>生产环境中 LoginUser 由 JWT / opaque token 转换器写入 {@code Authentication#getPrincipal()}，
 * {@code SecurityUtils}、{@code @Permission}、MyBatis 审计填充都从这里读取。本类按同样的约定构造
 * Authentication：principal 为 LoginUser，角色映射为 {@code ROLE_xxx}，权限编码原样作为 authority。</p>
 */
public final class ChaosSecurityTestSupport {

    private ChaosSecurityTestSupport() {
    }

    /**
     * 构造已认证的 Authentication。
     */
    public static Authentication authentication(LoginUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.roles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        user.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return UsernamePasswordAuthenticationToken.authenticated(user, "N/A", authorities);
    }

    /**
     * 在当前线程写入登录用户（SecurityContextHolder + RequestContext 的用户/租户），关闭作用域时恢复。
     *
     * <pre>{@code
     * try (ChaosSecurityTestSupport.Scope ignored = ChaosSecurityTestSupport.withLoginUser(admin)) {
     *     assertThat(SecurityUtils.hasPermission("order:delete")).isTrue();
     * }
     * }</pre>
     */
    public static Scope withLoginUser(LoginUser user) {
        SecurityContext previousSecurity = SecurityContextHolder.getContext();
        Optional<RequestContextSnapshot> previousRequest = RequestContext.current();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(user));
        SecurityContextHolder.setContext(context);
        RequestContextSnapshot base = previousRequest.orElseGet(RequestContextSnapshot::empty);
        RequestContext.set(base.withTenantId(user.tenantId()).withUserId(user.userId()));
        return new Scope(previousSecurity, previousRequest.orElse(null));
    }

    /**
     * 登录用户作用域。
     */
    public static final class Scope implements AutoCloseable {

        private final SecurityContext previousSecurity;

        private final RequestContextSnapshot previousRequest;

        private boolean closed;

        private Scope(SecurityContext previousSecurity, RequestContextSnapshot previousRequest) {
            this.previousSecurity = previousSecurity;
            this.previousRequest = previousRequest;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previousSecurity == null || previousSecurity.getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(previousSecurity);
            }
            if (previousRequest == null) {
                RequestContext.clear();
            } else {
                RequestContext.set(previousRequest);
            }
        }
    }
}
