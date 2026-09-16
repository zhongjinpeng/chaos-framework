package com.michael.chaos.authorization.token;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.authorization.kickout.AuthorizationSession;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.AuthorizationTokenRevoker;
import com.michael.chaos.security.api.token.JwtRevocationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;

/**
 * 增强 OAuth2 token 撤销认证提供者。
 *
 * <p>官方 Provider 负责客户端校验和授权存储失效标记；本实现额外把 access token 写入
 * JWT 撤销服务，并清理登录互踢会话索引。</p>
 */
public class ChaosTokenRevocationAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;

    private final AuthorizationSessionRegistry sessionRegistry;

    private final AuthorizationTokenRevoker tokenRevoker;

    private final OAuth2TokenRevocationAuthenticationProvider delegate;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建增强撤销 Provider。
     */
    public ChaosTokenRevocationAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            AuthorizationSessionRegistry sessionRegistry,
            JwtRevocationService revocationService,
            AuditEventPublisher auditEventPublisher) {
        this.authorizationService = authorizationService;
        this.sessionRegistry = sessionRegistry;
        this.tokenRevoker = new AuthorizationTokenRevoker(revocationService);
        this.delegate = new OAuth2TokenRevocationAuthenticationProvider(authorizationService);
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 先执行官方撤销逻辑，成功后同步 JWT 黑名单和互踢索引。
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2TokenRevocationAuthenticationToken revocation =
                (OAuth2TokenRevocationAuthenticationToken) authentication;
        OAuth2Authorization authorization = authorizationService.findByToken(revocation.getToken(), null);
        Authentication result = delegate.authenticate(authentication);
        if (result.isAuthenticated() && authorization != null) {
            tokenRevoker.revokeAccessToken(AuthorizationSession.from(authorization));
            sessionRegistry.remove(authorization);
            auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_TOKEN_REVOKE, AuditOutcome.SUCCESS)
                    .principalId(authorization.getPrincipalName())
                    .clientId(authorization.getRegisteredClientId())
                    .attributes(AuditSupport.attributes("authorizationId", authorization.getId()))
                    .build());
        }
        return result;
    }

    /**
     * 仅处理 OAuth2 token 撤销请求。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
