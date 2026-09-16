package com.michael.chaos.authorization.token;

import com.michael.chaos.authorization.grant.LoginUserAuthenticationToken;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * JWT claim 定制器。
 */
public class ChaosTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /**
     * 将 token 标识、客户端、grant_type、用户、租户、角色和权限写入 JWT claim。
     */
    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        context.getClaims()
                .id(UUID.randomUUID().toString())
                .claim(ChaosJwtClaims.CLIENT_ID, context.getRegisteredClient().getClientId())
                .claim(ChaosJwtClaims.GRANT_TYPE, context.getAuthorizationGrantType().getValue());
        AuthorizationLoginContext loginContext = loginContext(context);
        if (loginContext.hasDeviceId()) {
            context.getClaims().claim(ChaosJwtClaims.DEVICE_ID, loginContext.deviceId());
        }

        LoginUser user = resolveLoginUser(context);
        if (user != null) {
            writeUserClaims(context, user);
            return;
        }
        copyUserClaimsFromAuthorization(context);
    }

    /**
     * 定制 opaque/reference access token claim，供 introspection 返回给资源服务器和网关。
     */
    public void customizeOpaque(OAuth2TokenClaimsContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        context.getClaims()
                .id(UUID.randomUUID().toString())
                .claim(ChaosJwtClaims.CLIENT_ID, context.getRegisteredClient().getClientId())
                .claim(ChaosJwtClaims.GRANT_TYPE, context.getAuthorizationGrantType().getValue());
        AuthorizationLoginContext loginContext = loginContext(context);
        if (loginContext.hasDeviceId()) {
            context.getClaims().claim(ChaosJwtClaims.DEVICE_ID, loginContext.deviceId());
        }

        LoginUser user = resolveLoginUser(context);
        if (user != null) {
            writeUserClaims(context, user);
        }
    }

    private LoginUser resolveLoginUser(JwtEncodingContext context) {
        if (context.getPrincipal() instanceof LoginUserAuthenticationToken authentication) {
            return authentication.getPrincipal();
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return null;
        }
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (principal instanceof LoginUserAuthenticationToken authentication) {
            return authentication.getPrincipal();
        }
        return null;
    }

    private LoginUser resolveLoginUser(OAuth2TokenClaimsContext context) {
        if (context.getPrincipal() instanceof LoginUserAuthenticationToken authentication) {
            return authentication.getPrincipal();
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return null;
        }
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (principal instanceof LoginUserAuthenticationToken authentication) {
            return authentication.getPrincipal();
        }
        return null;
    }

    private AuthorizationLoginContext loginContext(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return AuthorizationLoginContext.empty();
        }
        return AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
    }

    private AuthorizationLoginContext loginContext(OAuth2TokenClaimsContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return AuthorizationLoginContext.empty();
        }
        return AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
    }

    private void writeUserClaims(JwtEncodingContext context, LoginUser user) {
        context.getClaims()
                .subject(user.userId())
                .claim(ChaosJwtClaims.USER_ID, user.userId())
                .claim(ChaosJwtClaims.USERNAME, user.username())
                .claim(ChaosJwtClaims.PREFERRED_USERNAME, user.username())
                .claim(ChaosJwtClaims.TENANT_ID, user.tenantId())
                .claim(ChaosJwtClaims.ROLES, user.roles())
                .claim(ChaosJwtClaims.PERMISSIONS, user.permissions());
    }

    private void writeUserClaims(OAuth2TokenClaimsContext context, LoginUser user) {
        context.getClaims()
                .subject(user.userId())
                .claim(ChaosJwtClaims.USER_ID, user.userId())
                .claim(ChaosJwtClaims.USERNAME, user.username())
                .claim(ChaosJwtClaims.PREFERRED_USERNAME, user.username())
                .claim(ChaosJwtClaims.TENANT_ID, user.tenantId())
                .claim(ChaosJwtClaims.ROLES, user.roles())
                .claim(ChaosJwtClaims.PERMISSIONS, user.permissions());
    }

    private void copyUserClaimsFromAuthorization(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null || authorization.getAccessToken() == null) {
            return;
        }
        Map<String, Object> claims = authorization.getAccessToken().getClaims();
        copyClaim(context, claims, JwtClaimNames.SUB);
        copyClaim(context, claims, ChaosJwtClaims.USER_ID);
        copyClaim(context, claims, ChaosJwtClaims.USERNAME);
        copyClaim(context, claims, ChaosJwtClaims.PREFERRED_USERNAME);
        copyClaim(context, claims, ChaosJwtClaims.TENANT_ID);
        copyClaim(context, claims, ChaosJwtClaims.ROLES);
        copyClaim(context, claims, ChaosJwtClaims.PERMISSIONS);
    }

    private void copyClaim(JwtEncodingContext context, Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value != null) {
            context.getClaims().claim(claimName, value);
        }
    }
}
