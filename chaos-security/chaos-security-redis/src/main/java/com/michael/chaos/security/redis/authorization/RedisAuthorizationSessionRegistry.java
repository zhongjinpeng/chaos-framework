package com.michael.chaos.security.redis.authorization;

import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.kickout.AuthorizationSession;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * 基于 Redis 的授权会话索引。
 *
 * <p>JWT token 模式下，授权对象可能存储在 JDBC 或内存中，Redis 索引用于跨实例定位旧授权 ID，
 * 从而实现多授权服务器实例下的登录互踢。</p>
 */
public class RedisAuthorizationSessionRegistry implements AuthorizationSessionRegistry {

    private static final Duration MIN_TTL = Duration.ofMinutes(1);

    private final RedisTemplate<Object, Object> redisTemplate;

    private final String prefix;

    /**
     * 创建 Redis 授权会话索引。
     */
    public RedisAuthorizationSessionRegistry(
            RedisTemplate<Object, Object> redisTemplate,
            ChaosAuthorizationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.prefix = normalizePrefix(properties.getKickout().getRedisKeyPrefix());
    }

    /**
     * 保存用户全局索引和用户加客户端索引。
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        if (authorization == null) {
            return;
        }
        AuthorizationSession session = AuthorizationSession.from(authorization);
        String clientKey = principalClientKey(session.principalName(), session.registeredClientId());
        String globalKey = principalGlobalKey(session.principalName());
        Duration ttl = authorizationTtl(authorization);
        redisTemplate.opsForValue().set(sessionKey(session.authorizationId()), session, ttl);
        redisTemplate.opsForSet().add(clientKey, session.authorizationId());
        redisTemplate.opsForSet().add(globalKey, session.authorizationId());
        if (session.deviceId() != null && !session.deviceId().isBlank()) {
            String deviceKey = principalClientDeviceKey(
                    session.principalName(),
                    session.registeredClientId(),
                    session.deviceId()
            );
            redisTemplate.opsForSet().add(deviceKey, session.authorizationId());
            redisTemplate.expire(deviceKey, ttl);
        }
        redisTemplate.expire(clientKey, ttl);
        redisTemplate.expire(globalKey, ttl);
    }

    /**
     * 查询用户在指定客户端下的授权会话。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipalAndClient(String principalName, String registeredClientId) {
        return readSessions(principalClientKey(principalName, registeredClientId));
    }

    /**
     * 查询用户在指定客户端和设备下的授权会话。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipalClientAndDevice(
            String principalName,
            String registeredClientId,
            String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Set.of();
        }
        return readSessions(principalClientDeviceKey(principalName, registeredClientId, deviceId));
    }

    /**
     * 查询用户全部授权会话。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipal(String principalName) {
        return readSessions(principalGlobalKey(principalName));
    }

    /**
     * 删除授权对象对应的索引。
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        if (authorization == null) {
            return;
        }
        remove(authorization.getPrincipalName(), authorization.getRegisteredClientId(), authorization.getId());
    }

    /**
     * 删除指定授权 ID 的索引。
     */
    @Override
    public void remove(String principalName, String registeredClientId, String authorizationId) {
        if (principalName == null || principalName.isBlank()
                || authorizationId == null || authorizationId.isBlank()) {
            return;
        }
        Object session = redisTemplate.opsForValue().get(sessionKey(authorizationId));
        redisTemplate.delete(sessionKey(authorizationId));
        redisTemplate.opsForSet().remove(principalGlobalKey(principalName), authorizationId);
        if (registeredClientId != null && !registeredClientId.isBlank()) {
            redisTemplate.opsForSet().remove(principalClientKey(principalName, registeredClientId), authorizationId);
        }
        if (session instanceof AuthorizationSession authorizationSession
                && authorizationSession.deviceId() != null
                && !authorizationSession.deviceId().isBlank()) {
            redisTemplate.opsForSet().remove(
                    principalClientDeviceKey(principalName, registeredClientId, authorizationSession.deviceId()),
                    authorizationId
            );
        }
    }

    private Set<AuthorizationSession> readSessions(String key) {
        Set<Object> values = redisTemplate.opsForSet().members(key);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<AuthorizationSession> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String authorizationId = value.toString();
            Object session = redisTemplate.opsForValue().get(sessionKey(authorizationId));
            if (session instanceof AuthorizationSession authorizationSession) {
                result.add(authorizationSession);
            } else {
                redisTemplate.opsForSet().remove(key, authorizationId);
            }
        }
        return result;
    }

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

    private String principalClientKey(String principalName, String registeredClientId) {
        return prefix + ":session:principal:" + principalName + ":client:" + registeredClientId;
    }

    private String principalGlobalKey(String principalName) {
        return prefix + ":session:principal:" + principalName + ":global";
    }

    private String principalClientDeviceKey(String principalName, String registeredClientId, String deviceId) {
        return prefix + ":session:principal:" + principalName + ":client:" + registeredClientId + ":device:" + deviceId;
    }

    private String sessionKey(String authorizationId) {
        return prefix + ":session:id:" + authorizationId;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "chaos:authorization:kickout";
        }
        return value.endsWith(":") ? value.substring(0, value.length() - 1) : value;
    }
}
