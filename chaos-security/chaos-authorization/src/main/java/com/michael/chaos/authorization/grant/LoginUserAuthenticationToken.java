package com.michael.chaos.authorization.grant;

import com.michael.chaos.security.api.auth.LoginUser;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 以 {@link LoginUser} 为 principal 的认证对象。
 */
public class LoginUserAuthenticationToken extends AbstractAuthenticationToken {

    /**
     * 本类会随 {@code OAuth2Authorization} 一起被 JDK 序列化写入 Redis，必须钉死 UID。
     *
     * <p>不写显式 UID 时，serialVersionUID 由类结构推导：字段、方法签名一改就变，框架升级后
     * 存量令牌全部读不回来（{@code InvalidClassException: local class incompatible}），
     * 表现是所有在线用户的请求报 500 而不是干净地重新登录。</p>
     */
    private static final long serialVersionUID = 1L;

    private final LoginUser principal;

    /**
     * 创建已认证用户认证对象。
     */
    public LoginUserAuthenticationToken(LoginUser principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * token grant 登录不暴露凭证。
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    /**
     * 返回已认证用户。
     */
    @Override
    public LoginUser getPrincipal() {
        return principal;
    }

    /**
     * 使用用户 ID 作为认证名称。
     */
    @Override
    public String getName() {
        return principal.userId();
    }

    /**
     * 将角色和权限转换为 Spring Security authority。
     */
    private static Set<GrantedAuthority> authorities(LoginUser user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        user.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        user.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    }
}
