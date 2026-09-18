package com.michael.chaos.authorization.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.test.audit.CapturingAuditEventPublisher;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Refresh token 增强 Provider 测试。
 */
class ChaosRefreshTokenAuthenticationProviderTest {

    /**
     * refresh token 找不到时应记录重放嫌疑审计。
     */
    @Test
    void shouldAuditReplayWhenRefreshTokenNotFound() {
        CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();
        ChaosRefreshTokenAuthenticationProvider provider = new ChaosRefreshTokenAuthenticationProvider(
                new InMemoryOAuth2AuthorizationService(),
                unsupportedTokenGenerator(),
                new InMemoryAuthorizationSessionRegistry(),
                publisher,
                new ChaosAuthorizationProperties()
        );
        OAuth2RefreshTokenAuthenticationToken token =
                new OAuth2RefreshTokenAuthenticationToken("missing-refresh-token", clientPrincipal(), null, null);

        assertThatThrownBy(() -> provider.authenticate(token))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(publisher.events()).hasSize(1);
        AuditEvent event = publisher.events().getFirst();
        assertThat(event.action()).isEqualTo("auth.refresh.replay");
        assertThat(event.attributes()).containsEntry("refreshTokenStatus", "not_found");
    }

    private OAuth2TokenGenerator<?> unsupportedTokenGenerator() {
        return context -> {
            throw new UnsupportedOperationException("not used in replay test");
        };
    }

    private OAuth2ClientAuthenticationToken clientPrincipal() {
        RegisteredClient client = RegisteredClient.withId("client-1")
                .clientId("client-1")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .build();
        return new OAuth2ClientAuthenticationToken(client, org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
    }

}
