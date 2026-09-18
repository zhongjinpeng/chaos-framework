package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.AuthorizationSession;
import com.michael.chaos.authorization.kickout.AuthorizationTokenRevoker;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import java.util.Set;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 基于 Redis 授权存储的互踢服务。
 */
public class RedisAuthorizationKickoutService implements AuthorizationKickoutService {

    private final RedisOAuth2AuthorizationService authorizationService;

    private final ChaosAuthorizationProperties properties;

    private final AuthorizationTokenRevoker tokenRevoker;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建 Redis 互踢服务。
     */
    public RedisAuthorizationKickoutService(
            RedisOAuth2AuthorizationService authorizationService,
            ChaosAuthorizationProperties properties) {
        this(authorizationService, properties, new NoopJwtRevocationService(), new NoopAuditEventPublisher());
    }

    /**
     * 创建 Redis 互踢服务。
     */
    public RedisAuthorizationKickoutService(
            RedisOAuth2AuthorizationService authorizationService,
            ChaosAuthorizationProperties properties,
            JwtRevocationService revocationService,
            AuditEventPublisher auditEventPublisher) {
        this.authorizationService = authorizationService;
        this.properties = properties;
        this.tokenRevoker = new AuthorizationTokenRevoker(revocationService);
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 根据配置范围撤销并删除当前用户既有授权。
     */
    @Override
    public void kickout(LoginUser user, RegisteredClient registeredClient, AuthorizationLoginContext loginContext) {
        if (!properties.getKickout().isEnabled()) {
            return;
        }
        Set<String> authorizationIds = authorizationIds(user, registeredClient, loginContext);
        authorizationIds.forEach(authorizationId -> revokeAndRemove(user, registeredClient, authorizationId));
    }

    private Set<String> authorizationIds(
            LoginUser user,
            RegisteredClient registeredClient,
            AuthorizationLoginContext loginContext) {
        return switch (properties.getKickout().getScope()) {
            case GLOBAL -> authorizationService.findIdsByPrincipal(user.userId());
            case DEVICE -> {
                if (loginContext == null || !loginContext.hasDeviceId()) {
                    yield authorizationService.findIdsByPrincipalAndClient(user.userId(), registeredClient.getId());
                }
                yield authorizationService.findIdsByPrincipalClientAndDevice(
                        user.userId(),
                        registeredClient.getId(),
                        loginContext.deviceId()
                );
            }
            case CLIENT -> authorizationService.findIdsByPrincipalAndClient(user.userId(), registeredClient.getId());
        };
    }

    private void revokeAndRemove(LoginUser user, RegisteredClient registeredClient, String authorizationId) {
        OAuth2Authorization authorization = authorizationService.findById(authorizationId);
        if (authorization == null) {
            removeStaleIndex(user, registeredClient, authorizationId);
            return;
        }
        tokenRevoker.revokeAccessToken(authorization);
        authorizationService.remove(authorization);
        AuthorizationSession session = AuthorizationSession.from(authorization);
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_SESSION_KICKOUT, AuditOutcome.SUCCESS)
                .principalId(user.userId())
                .tenantId(user.tenantId())
                .clientId(registeredClient.getId())
                .ip(session.ip())
                .attributes(session.auditAttributes())
                .build());
    }


    private void removeStaleIndex(LoginUser user, RegisteredClient registeredClient, String authorizationId) {
        String clientId = properties.getKickout().getScope() == ChaosAuthorizationProperties.Scope.CLIENT
                ? registeredClient.getId()
                : null;
        authorizationService.removePrincipalIndex(user.userId(), clientId, authorizationId);
    }
}
