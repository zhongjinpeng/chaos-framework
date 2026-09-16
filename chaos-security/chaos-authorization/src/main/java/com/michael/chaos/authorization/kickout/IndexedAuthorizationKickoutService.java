package com.michael.chaos.authorization.kickout;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.token.JwtRevocationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 基于授权会话索引的互踢服务。
 *
 * <p>该实现适用于 JWT token 模式以及 JDBC/内存授权存储。它通过会话索引定位旧授权，
 * 删除授权记录并写入 JWT 撤销黑名单，从而让旧 access token 尽快失效。</p>
 */
public class IndexedAuthorizationKickoutService implements AuthorizationKickoutService {

    private final OAuth2AuthorizationService authorizationService;

    private final AuthorizationSessionRegistry sessionRegistry;

    private final ChaosAuthorizationProperties properties;

    private final AuthorizationTokenRevoker tokenRevoker;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建基于会话索引的互踢服务。
     */
    public IndexedAuthorizationKickoutService(
            OAuth2AuthorizationService authorizationService,
            AuthorizationSessionRegistry sessionRegistry,
            JwtRevocationService revocationService,
            ChaosAuthorizationProperties properties,
            AuditEventPublisher auditEventPublisher) {
        this.authorizationService = authorizationService;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.tokenRevoker = new AuthorizationTokenRevoker(revocationService);
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 根据互踢范围定位旧授权并执行撤销。
     */
    @Override
    public void kickout(LoginUser user, RegisteredClient registeredClient, AuthorizationLoginContext loginContext) {
        if (!properties.getKickout().isEnabled()) {
            return;
        }
        Set<AuthorizationSession> sessions = sessions(user, registeredClient, loginContext);
        sessions.forEach(session -> revokeAndRemove(session));
    }

    /**
     * 记录新授权，供下一次登录互踢时查询。
     */
    @Override
    public void record(
            LoginUser user,
            RegisteredClient registeredClient,
            OAuth2Authorization authorization,
            AuthorizationLoginContext loginContext) {
        if (properties.getKickout().isEnabled()) {
            sessionRegistry.save(authorization);
        }
    }

    private Set<AuthorizationSession> sessions(
            LoginUser user,
            RegisteredClient registeredClient,
            AuthorizationLoginContext loginContext) {
        return switch (properties.getKickout().getScope()) {
            case GLOBAL -> sessionRegistry.findByPrincipal(user.userId());
            case DEVICE -> {
                if (loginContext == null || !loginContext.hasDeviceId()) {
                    yield sessionRegistry.findByPrincipalAndClient(user.userId(), registeredClient.getId());
                }
                yield sessionRegistry.findByPrincipalClientAndDevice(
                        user.userId(),
                        registeredClient.getId(),
                        loginContext.deviceId()
                );
            }
            case CLIENT -> sessionRegistry.findByPrincipalAndClient(user.userId(), registeredClient.getId());
        };
    }

    private void revokeAndRemove(AuthorizationSession session) {
        OAuth2Authorization authorization = authorizationService.findById(session.authorizationId());
        if (authorization == null) {
            tokenRevoker.revokeAccessToken(session);
            sessionRegistry.remove(session);
            return;
        }
        tokenRevoker.revokeAccessToken(authorization);
        authorizationService.remove(authorization);
        sessionRegistry.remove(authorization);
        auditKickout(session);
    }

    private void auditKickout(AuthorizationSession session) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_SESSION_KICKOUT, AuditOutcome.SUCCESS)
                .principalId(session.principalName())
                .clientId(session.registeredClientId())
                .ip(session.ip())
                .attributes(auditAttributes(session))
                .build());
    }

    private Map<String, String> auditAttributes(AuthorizationSession session) {
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfNotBlank(attributes, "authorizationId", session.authorizationId());
        putIfNotBlank(attributes, "grantType", session.grantType());
        putIfNotBlank(attributes, "deviceId", session.deviceId());
        putIfNotBlank(attributes, "userAgent", session.userAgent());
        return Map.copyOf(attributes);
    }

    private void putIfNotBlank(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
