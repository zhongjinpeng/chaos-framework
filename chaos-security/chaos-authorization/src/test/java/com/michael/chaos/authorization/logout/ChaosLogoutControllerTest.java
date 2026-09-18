package com.michael.chaos.authorization.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.AuthorizationTokenRevoker;
import com.michael.chaos.security.api.token.JwtRevocationService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * 框架统一退出登录端点行为测试。
 */
class ChaosLogoutControllerTest {

    /**
     * 没有合法 Bearer 头时返回未撤销而不是抛异常：注销接口被随手调用不应该产生 5xx。
     */
    @Test
    void returnsFalseWhenAuthorizationHeaderMissingOrMalformed() {
        OAuth2AuthorizationService authzService = mock(OAuth2AuthorizationService.class);
        ChaosLogoutController controller = new ChaosLogoutController(authzService, null, null, new NoopAuditEventPublisher());

        assertThat(controller.logout(null)).containsEntry("revoked", false);
        assertThat(controller.logout("")).containsEntry("revoked", false);
        assertThat(controller.logout("token-without-bearer-prefix")).containsEntry("revoked", false);
        verify(authzService, never()).findByToken(any(), any());
    }

    /**
     * token 查不到对应授权（已过期或已注销）时按未撤销返回，保持接口幂等。
     */
    @Test
    void returnsFalseWhenAuthorizationNotFound() {
        OAuth2AuthorizationService authzService = mock(OAuth2AuthorizationService.class);
        when(authzService.findByToken("missing-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);
        ChaosLogoutController controller = new ChaosLogoutController(authzService, null, null, new NoopAuditEventPublisher());

        Map<String, Object> result = controller.logout("Bearer missing-token");

        assertThat(result).containsEntry("revoked", false);
        verify(authzService, never()).remove(any());
    }

    /**
     * 找到授权时必须真正删除，否则 refresh token 还能继续换新 token。
     */
    @Test
    void removesAuthorizationWhenFound() {
        OAuth2AuthorizationService authzService = mock(OAuth2AuthorizationService.class);
        OAuth2Authorization authz = mock(OAuth2Authorization.class);
        when(authzService.findByToken("good-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authz);
        ChaosLogoutController controller = new ChaosLogoutController(authzService, null, null, new NoopAuditEventPublisher());

        Map<String, Object> result = controller.logout("Bearer good-token");

        assertThat(result).containsEntry("revoked", true);
        verify(authzService, times(1)).remove(authz);
    }

    /**
     * JWT 模式下退出必须把 access token 写入撤销服务并清理会话索引，否则注销后 token 在有效期内仍可使用。
     */
    @Test
    void revokesAccessTokenAndRemovesSessionOnLogout() {
        OAuth2AuthorizationService authzService = mock(OAuth2AuthorizationService.class);
        AuthorizationSessionRegistry sessionRegistry = mock(AuthorizationSessionRegistry.class);
        JwtRevocationService revocationService = mock(JwtRevocationService.class);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "good-token", Instant.now(), Instant.now().plusSeconds(600));
        OAuth2Authorization authz = mock(OAuth2Authorization.class);
        OAuth2Authorization.Token<OAuth2AccessToken> token = mock(OAuth2Authorization.Token.class);
        when(token.getToken()).thenReturn(accessToken);
        when(token.getClaims()).thenReturn(Map.of("jti", "jti-1"));
        when(authz.getAccessToken()).thenReturn(token);
        when(authzService.findByToken("good-token", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authz);
        ChaosLogoutController controller = new ChaosLogoutController(
                authzService, sessionRegistry, new AuthorizationTokenRevoker(revocationService),
                new NoopAuditEventPublisher());

        Map<String, Object> result = controller.logout("Bearer good-token");

        assertThat(result).containsEntry("revoked", true);
        verify(revocationService).revoke(org.mockito.ArgumentMatchers.eq("jti-1"), any(Duration.class));
        verify(sessionRegistry).remove(authz);
        verify(authzService).remove(authz);
    }
}
