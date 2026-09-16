package com.michael.chaos.security.oauth2;

import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;

/**
 * 将 opaque token introspection 结果转换为框架统一的 {@link LoginUser} 主体。
 */
public class LoginUserOpaqueTokenAuthenticationConverter implements OpaqueTokenAuthenticationConverter {

    /**
     * 基于 introspection claim 构建认证对象。
     */
    @Override
    public Authentication convert(String introspectedToken, OAuth2AuthenticatedPrincipal authenticatedPrincipal) {
        Map<String, Object> attributes = authenticatedPrincipal.getAttributes();
        Set<String> roles = claimAsSet(attributes.get(ChaosJwtClaims.ROLES));
        Set<String> permissions = claimAsSet(attributes.get(ChaosJwtClaims.PERMISSIONS));
        String userId = firstNonBlank(
                stringValue(attributes.get(ChaosJwtClaims.USER_ID)),
                stringValue(attributes.get(OAuth2TokenIntrospectionClaimNames.SUB))
        );
        String username = firstNonBlank(
                stringValue(attributes.get(ChaosJwtClaims.USERNAME)),
                stringValue(attributes.get(ChaosJwtClaims.PREFERRED_USERNAME)),
                stringValue(attributes.get(OAuth2TokenIntrospectionClaimNames.USERNAME)),
                userId
        );
        LoginUser loginUser = new LoginUser(
                userId,
                username,
                stringValue(attributes.get(ChaosJwtClaims.TENANT_ID)),
                roles,
                permissions
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                introspectedToken,
                instantValue(attributes.get(OAuth2TokenIntrospectionClaimNames.IAT)),
                instantValue(attributes.get(OAuth2TokenIntrospectionClaimNames.EXP)),
                claimAsSet(attributes.get(OAuth2TokenIntrospectionClaimNames.SCOPE))
        );
        return new BearerTokenAuthentication(
                new LoginUserOpaqueTokenPrincipal(loginUser, attributes),
                accessToken,
                authorities(roles, permissions)
        );
    }

    private Collection<GrantedAuthority> authorities(Set<String> roles, Set<String> permissions) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return Set.copyOf(authorities);
    }

    private Set<String> claimAsSet(Object claim) {
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
            Arrays.stream(value.split("[, ]"))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .forEach(result::add);
            return Set.copyOf(result);
        }
        return Set.of();
    }

    private Instant instantValue(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
