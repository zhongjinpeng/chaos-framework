package com.michael.chaos.security.redis.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Redis 授权存储测试。
 */
class RedisOAuth2AuthorizationServiceTest {

    /**
     * 同一个授权 ID 保存新 refresh token 时，应清理旧 refresh token 索引。
     */
    @Test
    void shouldRemoveOldRefreshTokenIndexWhenAuthorizationRotated() {
        CapturingRedisTemplate redisTemplate = new CapturingRedisTemplate();
        RedisOAuth2AuthorizationService service =
                new RedisOAuth2AuthorizationService(redisTemplate, new ChaosAuthorizationProperties());
        OAuth2Authorization first = authorization("auth-1", "access-1", "refresh-1");
        OAuth2Authorization second = authorization("auth-1", "access-2", "refresh-2");

        service.save(first);
        service.save(second);

        assertThat(redisTemplate.deletedKeys()).contains(
                "chaos:authorization:token:refresh_token:refresh-1",
                "chaos:authorization:token:access_token:access-1"
        );
        assertThat(redisTemplate.setKeys()).contains("chaos:authorization:token:refresh_token:refresh-2");
    }

    private static final class CapturingRedisTemplate extends RedisTemplate<Object, Object> {

        private final List<Object> deletedKeys = new ArrayList<>();

        private final List<Object> setKeys = new ArrayList<>();

        private int authorizationReadCount;

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<Object, Object> opsForValue() {
            return (ValueOperations<Object, Object>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                            setKeys.add(args[0]);
                            return null;
                        }
                        if ("get".equals(method.getName()) && args != null && args.length == 1) {
                            if ("chaos:authorization:authorization:auth-1".equals(args[0])
                                    && authorizationReadCount++ == 1) {
                                return RedisOAuth2AuthorizationServiceTest.authorization(
                                        "auth-1",
                                        "access-1",
                                        "refresh-1"
                                );
                            }
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public SetOperations<Object, Object> opsForSet() {
            return (SetOperations<Object, Object>) Proxy.newProxyInstance(
                    SetOperations.class.getClassLoader(),
                    new Class<?>[]{SetOperations.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType())
            );
        }

        @Override
        public Boolean delete(Object key) {
            deletedKeys.add(key);
            return true;
        }

        @Override
        public Boolean expire(Object key, Duration timeout) {
            return true;
        }

        private List<Object> deletedKeys() {
            return deletedKeys;
        }

        private List<Object> setKeys() {
            return setKeys;
        }

        private Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class || type == int.class || type == short.class || type == byte.class) {
                return 0;
            }
            return null;
        }
    }

    private static OAuth2Authorization authorization(String authorizationId, String accessTokenValue, String refreshTokenValue) {
        RegisteredClient client = RegisteredClient.withId("client-1")
                .clientId("client-1")
                .clientSecret("{noop}secret")
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                accessTokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                refreshTokenValue,
                Instant.now(),
                Instant.now().plusSeconds(7200)
        );
        return OAuth2Authorization.withRegisteredClient(client)
                .id(authorizationId)
                .principalName("1001")
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
