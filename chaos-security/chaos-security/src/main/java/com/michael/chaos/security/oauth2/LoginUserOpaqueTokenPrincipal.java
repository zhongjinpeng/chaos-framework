package com.michael.chaos.security.oauth2;

import com.michael.chaos.security.api.auth.LoginUser;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

/**
 * opaque token 模式下承载 {@link LoginUser} 的认证主体。
 */
public class LoginUserOpaqueTokenPrincipal implements OAuth2AuthenticatedPrincipal, Serializable {

    private static final long serialVersionUID = 1L;

    private final LoginUser loginUser;

    private final Map<String, Object> attributes;

    /**
     * 创建 opaque token 认证主体。
     */
    public LoginUserOpaqueTokenPrincipal(LoginUser loginUser, Map<String, Object> attributes) {
        this.loginUser = loginUser;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 返回框架统一登录用户。
     */
    public LoginUser loginUser() {
        return loginUser;
    }

    /**
     * 返回 introspection claim。
     */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 权限已经写入 {@code BearerTokenAuthentication}，主体自身不重复维护权限集合。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Set.of();
    }

    /**
     * 使用用户名作为主体名称。
     */
    @Override
    public String getName() {
        return loginUser.username();
    }

}
