package com.michael.chaos.authorization.kickout;

import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.JwtTokenIds;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * 授权 access token 撤销器。
 *
 * <p>登录互踢删除授权记录之前，先把旧 access token 写入撤销服务。JWT 模式下资源服务器
 * 可通过黑名单立即拒绝旧 token；引用 token 模式下删除授权记录即可让旧 token 失效。</p>
 */
public final class AuthorizationTokenRevoker {

    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(2);

    private final JwtRevocationService revocationService;

    public AuthorizationTokenRevoker(JwtRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    /**
     * 撤销授权对象中的 access token。
     */
    public void revokeAccessToken(OAuth2Authorization authorization) {
        if (authorization == null || authorization.getAccessToken() == null) {
            return;
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        String tokenId = resolveTokenId(accessToken);
        if (tokenId == null || tokenId.isBlank()) {
            return;
        }
        revocationService.revoke(tokenId, ttl(accessToken.getToken().getExpiresAt()));
    }

    /**
     * 按授权会话摘要撤销 access token。
     */
    public void revokeAccessToken(AuthorizationSession session) {
        if (session == null || session.accessTokenId() == null || session.accessTokenId().isBlank()) {
            return;
        }
        revocationService.revoke(session.accessTokenId(), ttl(session.accessTokenExpiresAt()));
    }

    public static String resolveTokenId(OAuth2Authorization.Token<OAuth2AccessToken> accessToken) {
        Object jti = accessToken.getClaims() == null ? null : accessToken.getClaims().get(JwtClaimNames.JTI);
        if (jti instanceof String value && !value.isBlank()) {
            return value;
        }
        String tokenValue = accessToken.getToken().getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        return JwtTokenIds.fromTokenValue(tokenValue);
    }

    private Duration ttl(Instant expiresAt) {
        if (expiresAt == null) {
            return DEFAULT_TOKEN_TTL;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }
}
