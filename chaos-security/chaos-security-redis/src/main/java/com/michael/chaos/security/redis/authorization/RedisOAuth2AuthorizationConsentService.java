package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/**
 * 基于 Redis 的 OAuth2 授权同意记录存储服务。
 */
public class RedisOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final RedisTemplate<Object, Object> redisTemplate;

    private final String prefix;

    public RedisOAuth2AuthorizationConsentService(
            RedisTemplate<Object, Object> redisTemplate,
            ChaosAuthorizationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.prefix = RedisKeyPrefixes.normalize(properties.getConsent().getRedisKeyPrefix(), "chaos:authorization:consent");
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        redisTemplate.opsForValue().set(key(
                authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName()), authorizationConsent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        if (authorizationConsent != null) {
            redisTemplate.delete(key(authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName()));
        }
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Object value = redisTemplate.opsForValue().get(key(registeredClientId, principalName));
        return value instanceof OAuth2AuthorizationConsent consent ? consent : null;
    }

    private String key(String registeredClientId, String principalName) {
        return prefix + ":" + encode(registeredClientId) + ":" + encode(principalName);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

}
