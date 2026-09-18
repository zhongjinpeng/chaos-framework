package com.michael.chaos.authorization.token;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditAttributes;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 增强 refresh_token 认证提供者。
 *
 * <p>官方 Provider 负责 OAuth2 标准校验、access token 重新签发和 refresh token 轮换。
 * 本实现额外记录刷新审计、刷新授权会话索引，并在疑似 refresh token 重放时写入审计事件。</p>
 */
public class ChaosRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;

    private final AuthorizationSessionRegistry sessionRegistry;

    private final AuditEventPublisher auditEventPublisher;

    private final ChaosAuthorizationProperties properties;

    private final OAuth2RefreshTokenAuthenticationProvider delegate;

    /**
     * 创建增强 refresh_token Provider。
     */
    public ChaosRefreshTokenAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationSessionRegistry sessionRegistry,
            AuditEventPublisher auditEventPublisher,
            ChaosAuthorizationProperties properties) {
        this.authorizationService = authorizationService;
        this.sessionRegistry = sessionRegistry;
        this.auditEventPublisher = auditEventPublisher;
        this.properties = properties;
        this.delegate = new OAuth2RefreshTokenAuthenticationProvider(authorizationService, tokenGenerator);
    }

    /**
     * 刷新 token，并在成功或重放嫌疑时记录审计。
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshAuthentication =
                (OAuth2RefreshTokenAuthenticationToken) authentication;
        OAuth2Authorization previousAuthorization = authorizationService.findByToken(
                refreshAuthentication.getRefreshToken(),
                OAuth2TokenType.REFRESH_TOKEN
        );
        try {
            Authentication result = delegate.authenticate(authentication);
            auditSuccess(result, previousAuthorization);
            refreshSessionIndex(result);
            return result;
        } catch (OAuth2AuthenticationException ex) {
            auditReplayIfNecessary(refreshAuthentication, previousAuthorization, ex);
            throw ex;
        }
    }

    /**
     * 仅处理 refresh_token 请求。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void auditSuccess(Authentication result, OAuth2Authorization previousAuthorization) {
        if (!(result instanceof OAuth2AccessTokenAuthenticationToken tokenAuthentication)) {
            return;
        }
        RegisteredClient registeredClient = tokenAuthentication.getRegisteredClient();
        OAuth2Authorization authorization = authorizationService.findByToken(
                tokenAuthentication.getAccessToken().getTokenValue(),
                OAuth2TokenType.ACCESS_TOKEN
        );
        AuthorizationLoginContext loginContext = loginContext(authorization, previousAuthorization);
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_REFRESH_SUCCESS, AuditOutcome.SUCCESS)
                .principalId(authorization == null ? "" : authorization.getPrincipalName())
                .clientId(registeredClient.getClientId())
                .ip(loginContext.ip())
                .attributes(auditAttributes(loginContext, rotated(previousAuthorization, authorization)))
                .build());
    }

    private void refreshSessionIndex(Authentication result) {
        if (!(result instanceof OAuth2AccessTokenAuthenticationToken tokenAuthentication)) {
            return;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(
                tokenAuthentication.getAccessToken().getTokenValue(),
                OAuth2TokenType.ACCESS_TOKEN
        );
        if (authorization != null) {
            sessionRegistry.save(authorization);
        }
    }

    private void auditReplayIfNecessary(
            OAuth2RefreshTokenAuthenticationToken refreshAuthentication,
            OAuth2Authorization previousAuthorization,
            OAuth2AuthenticationException ex) {
        if (!properties.getRefreshToken().isSecurityEnabled()
                || !properties.getRefreshToken().isAuditReplayEnabled()
                || previousAuthorization != null) {
            return;
        }
        OAuth2ClientAuthenticationToken clientAuthentication = clientAuthentication(refreshAuthentication);
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_REFRESH_REPLAY, AuditOutcome.DENIED)
                .clientId(clientAuthentication == null || clientAuthentication.getRegisteredClient() == null
                        ? ""
                        : clientAuthentication.getRegisteredClient().getClientId())
                .reason(ex.getError().getErrorCode())
                .attributes(AuditSupport.attributes("refreshTokenStatus", "not_found"))
                .build());
    }

    private OAuth2ClientAuthenticationToken clientAuthentication(OAuth2RefreshTokenAuthenticationToken authentication) {
        Object principal = authentication.getPrincipal();
        return principal instanceof OAuth2ClientAuthenticationToken clientAuthentication ? clientAuthentication : null;
    }

    private AuthorizationLoginContext loginContext(
            OAuth2Authorization authorization,
            OAuth2Authorization previousAuthorization) {
        if (authorization != null) {
            AuthorizationLoginContext context =
                    AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
            if (hasAnyValue(context)) {
                return context;
            }
        }
        return previousAuthorization == null
                ? AuthorizationLoginContext.empty()
                : AuthorizationLoginContext.fromAttributes(previousAuthorization.getAttributes());
    }

    private boolean rotated(OAuth2Authorization previousAuthorization, OAuth2Authorization authorization) {
        if (previousAuthorization == null || authorization == null
                || previousAuthorization.getRefreshToken() == null || authorization.getRefreshToken() == null) {
            return false;
        }
        String previous = previousAuthorization.getRefreshToken().getToken().getTokenValue();
        String current = authorization.getRefreshToken().getToken().getTokenValue();
        return !previous.equals(current);
    }

    private boolean hasAnyValue(AuthorizationLoginContext context) {
        return context != null && (!context.grantType().isBlank()
                || !context.deviceId().isBlank()
                || !context.ip().isBlank()
                || !context.userAgent().isBlank());
    }

    private Map<String, String> auditAttributes(AuthorizationLoginContext loginContext, boolean rotated) {
        return AuditAttributes.create()
                .putIfNotBlank("grantType", OAuth2ParameterNames.REFRESH_TOKEN)
                .putIfNotBlank("deviceId", loginContext.deviceId())
                .putIfNotBlank("userAgent", loginContext.userAgent())
                .put("rotated", rotated)
                .build();
    }

}
