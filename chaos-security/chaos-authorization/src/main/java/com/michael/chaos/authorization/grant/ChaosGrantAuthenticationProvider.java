package com.michael.chaos.authorization.grant;

import com.michael.chaos.audit.AuditAction;
import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditOutcome;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.authorization.session.AuthorizationSessionAttributes;
import com.michael.chaos.authorization.session.AuthorizationSessionParameterNames;
import com.michael.chaos.security.api.auth.ChaosJwtClaims;
import com.michael.chaos.security.api.auth.LoginUser;
import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.StringUtils;

/**
 * 自定义 grant_type 认证提供者。
 *
 * <p>该提供者负责调用业务登录处理器、执行互踢策略、生成 access token/refresh token，
 * 并保存 {@link OAuth2Authorization}。</p>
 */
public class ChaosGrantAuthenticationProvider implements AuthenticationProvider {

    private final Map<String, ChaosGrantAuthenticationHandler> handlers;

    private final OAuth2AuthorizationService authorizationService;

    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private final AuthorizationKickoutService kickoutService;

    private final AuditEventPublisher auditEventPublisher;

    /**
     * 创建自定义 grant_type 认证提供者。
     */
    public ChaosGrantAuthenticationProvider(
            List<ChaosGrantAuthenticationHandler> handlers,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            AuthorizationKickoutService kickoutService,
            AuditEventPublisher auditEventPublisher) {
        this.handlers = new LinkedHashMap<>();
        handlers.forEach(handler -> this.handlers.put(handler.grantType().getValue(), handler));
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.kickoutService = kickoutService;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * 完成自定义 grant_type 登录认证和 token 签发。
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        ChaosGrantAuthenticationToken grantAuthentication = (ChaosGrantAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(grantAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (!registeredClient.getAuthorizationGrantTypes().contains(grantAuthentication.getGrantType())) {
            throw oauth2(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                    "client is not authorized for grant_type: " + grantAuthentication.getGrantType().getValue());
        }

        ChaosGrantAuthenticationHandler handler = handlers.get(grantAuthentication.getGrantType().getValue());
        if (handler == null) {
            throw oauth2(OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE,
                    "unsupported grant_type: " + grantAuthentication.getGrantType().getValue());
        }

        AuthorizationLoginContext loginContext = loginContext(grantAuthentication);
        Map<String, Object> loginParameters = grantAuthentication.getAdditionalParameters();
        LoginUser loginUser;
        try {
            loginUser = handler.authenticate(loginParameters);
        } catch (AuthenticationException ex) {
            publishLoginFailure(registeredClient, loginParameters, loginContext, ex);
            throw ex;
        }
        kickoutService.kickout(loginUser, registeredClient, loginContext);
        LoginUserAuthenticationToken userPrincipal = new LoginUserAuthenticationToken(loginUser);
        Set<String> authorizedScopes = resolveAuthorizedScopes(registeredClient, grantAuthentication.getAdditionalParameters());

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(loginUser.userId())
                .authorizationGrantType(grantAuthentication.getGrantType())
                .authorizedScopes(authorizedScopes)
                .attribute(Principal.class.getName(), userPrincipal)
                .attribute(AuthorizationSessionAttributes.LOGIN_CONTEXT, loginContext);

        OAuth2AccessToken accessToken = generateAccessToken(
                registeredClient, userPrincipal, grantAuthentication, authorizedScopes, authorizationBuilder);
        OAuth2RefreshToken refreshToken = generateRefreshToken(
                registeredClient, userPrincipal, grantAuthentication, authorizedScopes, authorizationBuilder);

        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);
        kickoutService.record(loginUser, registeredClient, authorization, loginContext);
        publishLoginSuccess(registeredClient, loginUser, loginContext);
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, accessToken, refreshToken, additionalParameters(loginUser));
    }

    /**
     * 发布登录失败审计。
     *
     * <p>失败事件的租户取自请求参数而不是登录用户：这时候还没认证成功，没有用户对象。</p>
     */
    private void publishLoginFailure(
            RegisteredClient registeredClient,
            Map<String, Object> loginParameters,
            AuthorizationLoginContext loginContext,
            AuthenticationException ex) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE)
                .tenantId(LoginAuditAttributes.tenant(loginParameters))
                .clientId(registeredClient.getClientId())
                .ip(loginContext.ip())
                .reason(ex.getMessage())
                .attributes(LoginAuditAttributes.from(loginParameters, loginContext))
                .build());
    }

    /**
     * 发布登录成功审计。
     */
    private void publishLoginSuccess(
            RegisteredClient registeredClient,
            LoginUser loginUser,
            AuthorizationLoginContext loginContext) {
        auditEventPublisher.publish(AuditSupport.event(AuditAction.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS)
                .principalId(loginUser.userId())
                .tenantId(loginUser.tenantId())
                .clientId(registeredClient.getClientId())
                .ip(loginContext.ip())
                .attributes(LoginAuditAttributes.from(loginUser.username(), loginContext))
                .build());
    }

    /**
     * 令牌响应里附带的用户信息。
     *
     * <p>键名复用 {@link ChaosJwtClaims}：资源服务器、网关按同一组常量解析，写死字符串迟早两边对不上。</p>
     */
    private Map<String, Object> additionalParameters(LoginUser loginUser) {
        return Map.of(
                ChaosJwtClaims.USER_ID, loginUser.userId(),
                ChaosJwtClaims.USERNAME, loginUser.username(),
                ChaosJwtClaims.TENANT_ID, loginUser.tenantId()
        );
    }

    /**
     * 判断是否支持当前认证请求类型。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return ChaosGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 生成 access token 并写入授权对象。
     */
    private OAuth2AccessToken generateAccessToken(
            RegisteredClient registeredClient,
            Authentication principal,
            ChaosGrantAuthenticationToken grantAuthentication,
            Set<String> authorizedScopes,
            OAuth2Authorization.Builder authorizationBuilder) {
        OAuth2TokenContext tokenContext = tokenContext(
                registeredClient, principal, grantAuthentication, OAuth2TokenType.ACCESS_TOKEN, authorizedScopes);
        OAuth2Token generatedToken = tokenGenerator.generate(tokenContext);
        if (generatedToken == null) {
            throw oauth2(OAuth2ErrorCodes.SERVER_ERROR, "access token generation failed");
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedToken.getTokenValue(),
                generatedToken.getIssuedAt(),
                generatedToken.getExpiresAt(),
                authorizedScopes
        );
        if (generatedToken instanceof ClaimAccessor claimAccessor) {
            authorizationBuilder.token(accessToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }
        return accessToken;
    }

    /**
     * 在客户端支持 refresh_token 时生成 refresh token。
     */
    private OAuth2RefreshToken generateRefreshToken(
            RegisteredClient registeredClient,
            Authentication principal,
            ChaosGrantAuthenticationToken grantAuthentication,
            Set<String> authorizedScopes,
            OAuth2Authorization.Builder authorizationBuilder) {
        if (!registeredClient.getAuthorizationGrantTypes()
                .contains(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)) {
            return null;
        }
        OAuth2TokenContext tokenContext = tokenContext(
                registeredClient, principal, grantAuthentication, OAuth2TokenType.REFRESH_TOKEN, authorizedScopes);
        OAuth2Token generatedToken = tokenGenerator.generate(tokenContext);
        if (generatedToken == null) {
            return null;
        }
        if (!(generatedToken instanceof OAuth2RefreshToken refreshToken)) {
            throw oauth2(OAuth2ErrorCodes.SERVER_ERROR, "refresh token generation failed");
        }
        authorizationBuilder.refreshToken(refreshToken);
        return refreshToken;
    }

    /**
     * 构建 Spring Authorization Server token 生成上下文。
     */
    private OAuth2TokenContext tokenContext(
            RegisteredClient registeredClient,
            Authentication principal,
            ChaosGrantAuthenticationToken grantAuthentication,
            OAuth2TokenType tokenType,
            Set<String> authorizedScopes) {
        return DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .tokenType(tokenType)
                .authorizationGrantType(grantAuthentication.getGrantType())
                .authorizationGrant(grantAuthentication)
                .build();
    }

    /**
     * 解析并校验请求授权范围。
     */
    private Set<String> resolveAuthorizedScopes(RegisteredClient registeredClient, Map<String, Object> parameters) {
        Object requestedScope = parameters.get(OAuth2ParameterNames.SCOPE);
        if (!(requestedScope instanceof String scope) || !StringUtils.hasText(scope)) {
            return registeredClient.getScopes();
        }
        Set<String> requestedScopes = Arrays.stream(scope.split(" "))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!registeredClient.getScopes().containsAll(requestedScopes)) {
            throw oauth2(OAuth2ErrorCodes.INVALID_SCOPE, "requested scope is not allowed");
        }
        return requestedScopes;
    }

    /**
     * 解析已认证的 OAuth2 客户端。
     */
    private OAuth2ClientAuthenticationToken authenticatedClient(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientPrincipal
                && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw oauth2(OAuth2ErrorCodes.INVALID_CLIENT, "client authentication is required");
    }

    /**
     * 创建 OAuth2 认证异常。
     */
    private OAuth2AuthenticationException oauth2(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }

    /**
     * 构建登录上下文。
     */
    private AuthorizationLoginContext loginContext(ChaosGrantAuthenticationToken grantAuthentication) {
        Map<String, Object> parameters = grantAuthentication.getAdditionalParameters();
        return new AuthorizationLoginContext(
                grantAuthentication.getGrantType().getValue(),
                stringValue(parameters.get(AuthorizationSessionParameterNames.DEVICE_ID)),
                stringValue(parameters.get("ip")),
                stringValue(parameters.get("user_agent"))
        );
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }
}
