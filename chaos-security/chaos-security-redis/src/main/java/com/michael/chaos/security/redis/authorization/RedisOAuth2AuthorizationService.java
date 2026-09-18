package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * 基于 Redis 的 OAuth2Authorization 存储服务。
 */
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final Duration MIN_TTL = Duration.ofMinutes(1);

    private final RedisTemplate<Object, Object> redisTemplate;

    private final String prefix;

    /**
     * 创建 Redis 授权存储服务。
     */
    public RedisOAuth2AuthorizationService(
            RedisTemplate<Object, Object> redisTemplate,
            ChaosAuthorizationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.prefix = RedisKeyPrefixes.normalize(properties.getToken().getRedisKeyPrefix(), "chaos:authorization");
    }

    /**
     * 保存授权对象并建立 token、用户和客户端索引。
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization existing = findById(authorization.getId());
        if (existing != null) {
            deleteTokenIndex(existing.getAccessToken());
            deleteTokenIndex(existing.getRefreshToken());
        }
        redisTemplate.opsForValue().set(authorizationKey(authorization.getId()), authorization, authorizationTtl(authorization));
        indexToken(authorization, authorization.getAccessToken());
        indexToken(authorization, authorization.getRefreshToken());
        indexPrincipal(authorization);
    }

    /**
     * 删除授权对象及其相关索引。
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        if (authorization == null) {
            return;
        }
        redisTemplate.delete(authorizationKey(authorization.getId()));
        deleteTokenIndex(authorization.getAccessToken());
        deleteTokenIndex(authorization.getRefreshToken());
        redisTemplate.opsForSet().remove(principalClientKey(authorization.getPrincipalName(), authorization.getRegisteredClientId()),
                authorization.getId());
        redisTemplate.opsForSet().remove(principalGlobalKey(authorization.getPrincipalName()), authorization.getId());
        AuthorizationLoginContext loginContext = AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
        if (loginContext.hasDeviceId()) {
            redisTemplate.opsForSet().remove(principalClientDeviceKey(
                            authorization.getPrincipalName(),
                            authorization.getRegisteredClientId(),
                            loginContext.deviceId()),
                    authorization.getId());
        }
    }

    /**
     * 根据授权 ID 查询授权对象。
     */
    @Override
    public OAuth2Authorization findById(String id) {
        Object value = StaleValueReader.read(redisTemplate, authorizationKey(id));
        return value instanceof OAuth2Authorization authorization ? authorization : null;
    }

    /**
     * 根据 token 查询授权对象。
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String authorizationId = authorizationIdByToken(token, tokenType);
        return authorizationId == null ? null : findById(authorizationId);
    }

    /**
     * 查询指定用户在指定客户端下的授权 ID 集合。
     */
    public Set<String> findIdsByPrincipalAndClient(String principalName, String registeredClientId) {
        return readSet(principalClientKey(principalName, registeredClientId));
    }

    /**
     * 查询指定用户在指定客户端和设备下的授权 ID 集合。
     */
    public Set<String> findIdsByPrincipalClientAndDevice(
            String principalName,
            String registeredClientId,
            String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Set.of();
        }
        return readSet(principalClientDeviceKey(principalName, registeredClientId, deviceId));
    }

    /**
     * 查询指定用户的全部授权 ID 集合。
     */
    public Set<String> findIdsByPrincipal(String principalName) {
        return readSet(principalGlobalKey(principalName));
    }

    /**
     * 根据授权 ID 删除授权。
     */
    public void removeById(String authorizationId) {
        OAuth2Authorization authorization = findById(authorizationId);
        if (authorization != null) {
            remove(authorization);
        }
    }

    /**
     * 删除指定授权 ID 的用户索引。
     *
     * <p>当授权对象已经过期或被外部清理时，互踢流程仍可用该方法清除残留索引。</p>
     */
    public void removePrincipalIndex(String principalName, String registeredClientId, String authorizationId) {
        if (principalName == null || principalName.isBlank()
                || authorizationId == null || authorizationId.isBlank()) {
            return;
        }
        redisTemplate.opsForSet().remove(principalGlobalKey(principalName), authorizationId);
        if (registeredClientId != null && !registeredClientId.isBlank()) {
            redisTemplate.opsForSet().remove(principalClientKey(principalName, registeredClientId), authorizationId);
        }
    }

    /**
     * 根据 token 反查授权 ID。
     */
    private String authorizationIdByToken(String token, OAuth2TokenType tokenType) {
        if (tokenType == null) {
            String accessTokenAuthorizationId = tokenAuthorizationId(tokenKey(OAuth2ParameterNames.ACCESS_TOKEN, token));
            if (accessTokenAuthorizationId != null) {
                return accessTokenAuthorizationId;
            }
            return tokenAuthorizationId(tokenKey(OAuth2ParameterNames.REFRESH_TOKEN, token));
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return tokenAuthorizationId(tokenKey(OAuth2ParameterNames.ACCESS_TOKEN, token));
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return tokenAuthorizationId(tokenKey(OAuth2ParameterNames.REFRESH_TOKEN, token));
        }
        return tokenAuthorizationId(tokenKey(tokenType.getValue(), token));
    }

    /**
     * 从 token 索引中读取授权 ID。
     */
    private String tokenAuthorizationId(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof String id ? id : null;
    }

    /**
     * 建立 token 到授权 ID 的索引。
     */
    private void indexToken(OAuth2Authorization authorization, OAuth2Authorization.Token<? extends OAuth2Token> token) {
        if (token == null) {
            return;
        }
        String tokenType = token.getToken() == authorization.getAccessToken().getToken()
                ? OAuth2ParameterNames.ACCESS_TOKEN
                : OAuth2ParameterNames.REFRESH_TOKEN;
        redisTemplate.opsForValue().set(tokenKey(tokenType, token.getToken().getTokenValue()), authorization.getId(), tokenTtl(token));
    }

    /**
     * 删除 token 索引。
     */
    private void deleteTokenIndex(OAuth2Authorization.Token<? extends OAuth2Token> token) {
        if (token == null) {
            return;
        }
        redisTemplate.delete(tokenKey(OAuth2ParameterNames.ACCESS_TOKEN, token.getToken().getTokenValue()));
        redisTemplate.delete(tokenKey(OAuth2ParameterNames.REFRESH_TOKEN, token.getToken().getTokenValue()));
    }

    /**
     * 建立用户维度授权索引。
     */
    private void indexPrincipal(OAuth2Authorization authorization) {
        String clientKey = principalClientKey(authorization.getPrincipalName(), authorization.getRegisteredClientId());
        String globalKey = principalGlobalKey(authorization.getPrincipalName());
        redisTemplate.opsForSet().add(clientKey, authorization.getId());
        redisTemplate.opsForSet().add(globalKey, authorization.getId());
        AuthorizationLoginContext loginContext = AuthorizationLoginContext.fromAttributes(authorization.getAttributes());
        if (loginContext.hasDeviceId()) {
            String deviceKey = principalClientDeviceKey(
                    authorization.getPrincipalName(),
                    authorization.getRegisteredClientId(),
                    loginContext.deviceId()
            );
            redisTemplate.opsForSet().add(deviceKey, authorization.getId());
            redisTemplate.expire(deviceKey, authorizationTtl(authorization));
        }
        redisTemplate.expire(clientKey, authorizationTtl(authorization));
        redisTemplate.expire(globalKey, authorizationTtl(authorization));
    }

    /**
     * 读取 Redis Set 并转换为字符串集合。
     */
    private Set<String> readSet(String key) {
        Set<Object> values = redisTemplate.opsForSet().members(key);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.stream().filter(Objects::nonNull).map(Object::toString).forEach(result::add);
        return result;
    }

    /**
     * 计算授权对象保留时间。
     */
    private Duration authorizationTtl(OAuth2Authorization authorization) {
        Instant expiresAt = null;
        if (authorization.getRefreshToken() != null) {
            expiresAt = authorization.getRefreshToken().getToken().getExpiresAt();
        }
        if (expiresAt == null && authorization.getAccessToken() != null) {
            expiresAt = authorization.getAccessToken().getToken().getExpiresAt();
        }
        if (expiresAt == null) {
            return MIN_TTL;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl.plus(MIN_TTL);
    }

    /**
     * 计算 token 索引保留时间。
     */
    private Duration tokenTtl(OAuth2Authorization.Token<? extends OAuth2Token> token) {
        Instant expiresAt = token.getToken().getExpiresAt();
        if (expiresAt == null) {
            return MIN_TTL;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl.plus(MIN_TTL);
    }

    /**
     * 构建授权对象 Redis key。
     */
    private String authorizationKey(String authorizationId) {
        return prefix + ":authorization:" + authorizationId;
    }

    /**
     * 构建 token 索引 Redis key。
     */
    private String tokenKey(String tokenType, String tokenValue) {
        return prefix + ":token:" + tokenType + ":" + tokenValue;
    }

    /**
     * 构建用户加客户端维度索引 key。
     */
    private String principalClientKey(String principalName, String registeredClientId) {
        return prefix + ":principal:" + principalName + ":client:" + registeredClientId;
    }

    /**
     * 构建用户全局维度索引 key。
     */
    private String principalGlobalKey(String principalName) {
        return prefix + ":principal:" + principalName + ":global";
    }

    /**
     * 构建用户、客户端和设备维度索引 key。
     */
    private String principalClientDeviceKey(String principalName, String registeredClientId, String deviceId) {
        return prefix + ":principal:" + principalName + ":client:" + registeredClientId + ":device:" + deviceId;
    }

    /**
     * 规范化 Redis key 前缀。
     */
}
