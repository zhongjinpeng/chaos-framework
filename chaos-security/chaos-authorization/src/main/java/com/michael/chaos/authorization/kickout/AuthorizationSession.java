package com.michael.chaos.authorization.kickout;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * 授权会话摘要。
 *
 * <p>该对象只保存互踢所需的最小信息，避免会话索引直接依赖完整授权对象序列化结构。
 * JWT 模式下，即使当前授权服务器实例查不到旧授权对象，也可以使用 access token 标识写入撤销黑名单。</p>
 *
 * @param authorizationId 授权 ID
 * @param principalName 用户主体标识
 * @param registeredClientId OAuth2 客户端 ID
 * @param accessTokenId access token 撤销标识
 * @param accessTokenExpiresAt access token 过期时间
 * @param grantType OAuth2 grant_type
 * @param deviceId 设备 ID
 * @param ip 客户端 IP
 * @param userAgent User-Agent
 */
public record AuthorizationSession(
        String authorizationId,
        String principalName,
        String registeredClientId,
        String accessTokenId,
        Instant accessTokenExpiresAt,
        String grantType,
        String deviceId,
        String ip,
        String userAgent
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 从完整授权对象提取会话摘要。
     */
    public static AuthorizationSession from(OAuth2Authorization authorization) {
        if (authorization == null) {
            throw new IllegalArgumentException("authorization must not be null");
        }
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
        String accessTokenId = accessToken == null ? null : AuthorizationTokenRevoker.resolveTokenId(accessToken);
        Instant accessTokenExpiresAt = accessToken == null ? null : accessToken.getToken().getExpiresAt();
        AuthorizationLoginContext loginContext = AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
        return new AuthorizationSession(
                authorization.getId(),
                authorization.getPrincipalName(),
                authorization.getRegisteredClientId(),
                accessTokenId,
                accessTokenExpiresAt,
                loginContext.grantType(),
                loginContext.deviceId(),
                loginContext.ip(),
                loginContext.userAgent()
        );
    }
}
