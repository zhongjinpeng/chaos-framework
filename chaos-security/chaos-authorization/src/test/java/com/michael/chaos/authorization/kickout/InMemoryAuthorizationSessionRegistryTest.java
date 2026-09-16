package com.michael.chaos.authorization.kickout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.authorization.session.AuthorizationSessionAttributes;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * 本地授权会话索引测试。
 */
class InMemoryAuthorizationSessionRegistryTest {

    /**
     * 应按用户、客户端和设备维度索引授权会话。
     */
    @Test
    void shouldIndexSessionByDevice() {
        InMemoryAuthorizationSessionRegistry registry = new InMemoryAuthorizationSessionRegistry();
        OAuth2Authorization authorization = authorization("auth-1", "device-a");

        registry.save(authorization);

        Set<AuthorizationSession> sessions =
                registry.findByPrincipalClientAndDevice("1001", "client-1", "device-a");
        assertThat(sessions).hasSize(1);
        AuthorizationSession session = sessions.iterator().next();
        assertThat(session.deviceId()).isEqualTo("device-a");
        assertThat(session.ip()).isEqualTo("10.0.0.1");
        assertThat(session.userAgent()).isEqualTo("JUnit");
    }

    /**
     * 删除授权会话时应同步清理设备索引。
     */
    @Test
    void shouldRemoveDeviceIndex() {
        InMemoryAuthorizationSessionRegistry registry = new InMemoryAuthorizationSessionRegistry();
        OAuth2Authorization authorization = authorization("auth-1", "device-a");
        registry.save(authorization);

        registry.remove(authorization);

        assertThat(registry.findByPrincipalClientAndDevice("1001", "client-1", "device-a")).isEmpty();
    }

    /**
     * 本地索引超过容量时应拒绝新会话，避免高基数登录无限占用内存。
     */
    @Test
    void shouldRejectNewSessionWhenCapacityExceeded() {
        InMemoryAuthorizationSessionRegistry registry = new InMemoryAuthorizationSessionRegistry(1);
        registry.save(authorization("auth-1", "device-a"));

        assertThatThrownBy(() -> registry.save(authorization("auth-2", "device-b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity exceeded");
    }

    /**
     * 更新已有授权 ID 不应受容量限制影响。
     */
    @Test
    void shouldAllowUpdatingExistingSessionWhenFull() {
        InMemoryAuthorizationSessionRegistry registry = new InMemoryAuthorizationSessionRegistry(1);
        registry.save(authorization("auth-1", "device-a"));

        registry.save(authorization("auth-1", "device-a"));

        assertThat(registry.findByPrincipalClientAndDevice("1001", "client-1", "device-a")).hasSize(1);
    }

    /**
     * 更新同一授权 ID 时应清理旧设备索引。
     */
    @Test
    void shouldRemoveOldDeviceIndexWhenUpdatingSession() {
        InMemoryAuthorizationSessionRegistry registry = new InMemoryAuthorizationSessionRegistry();
        registry.save(authorization("auth-1", "device-a"));

        registry.save(authorization("auth-1", "device-b"));

        assertThat(registry.findByPrincipalClientAndDevice("1001", "client-1", "device-a")).isEmpty();
        assertThat(registry.findByPrincipalClientAndDevice("1001", "client-1", "device-b")).hasSize(1);
    }

    private OAuth2Authorization authorization(String authorizationId, String deviceId) {
        RegisteredClient client = RegisteredClient.withId("client-1")
                .clientId("client-1")
                .clientSecret("{noop}secret")
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .tokenSettings(TokenSettings.builder().build())
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return OAuth2Authorization.withRegisteredClient(client)
                .id(authorizationId)
                .principalName("1001")
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .attribute(AuthorizationSessionAttributes.LOGIN_CONTEXT,
                        new AuthorizationLoginContext("password", deviceId, "10.0.0.1", "JUnit"))
                .accessToken(accessToken)
                .build();
    }
}
