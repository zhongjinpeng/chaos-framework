package com.michael.chaos.security.oauth2;

import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 将 JWT claim 转换为框架统一的 {@link LoginUser} 主体。
 */
public class LoginUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * 读取授权中心写入的用户、租户、角色和权限 claim。
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = claimAsSet(jwt, ChaosJwtClaims.ROLES);
        Set<String> permissions = claimAsSet(jwt, ChaosJwtClaims.PERMISSIONS);
        String userId = firstNonBlank(jwt.getClaimAsString(ChaosJwtClaims.USER_ID), jwt.getSubject());
        String username = firstNonBlank(
                jwt.getClaimAsString(ChaosJwtClaims.USERNAME),
                jwt.getClaimAsString(ChaosJwtClaims.PREFERRED_USERNAME),
                userId
        );
        LoginUser loginUser = new LoginUser(
                userId,
                username,
                jwt.getClaimAsString(ChaosJwtClaims.TENANT_ID),
                roles,
                permissions
        );
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return new JwtAuthenticationToken(jwt, authorities, loginUser.username()) {
            @Override
            public Object getPrincipal() {
                return loginUser;
            }
        };
    }

    /**
     * 将字符串或集合 claim 统一转换为不可变集合。
     */
    private Set<String> claimAsSet(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (claim instanceof Collection<?> values) {
            Set<String> result = new HashSet<>();
            for (Object value : values) {
                if (value != null && !value.toString().isBlank()) {
                    result.add(value.toString());
                }
            }
            return Set.copyOf(result);
        }
        if (claim instanceof String value && !value.isBlank()) {
            Set<String> result = new HashSet<>();
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .forEach(result::add);
            return Set.copyOf(result);
        }
        return Set.of();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
