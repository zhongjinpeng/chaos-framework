package com.michael.chaos.authorization.kickout;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * 基于本地内存的授权会话索引。
 *
 * <p>该实现适合单实例开发和测试环境。生产多实例部署应优先使用 Redis 索引，
 * 避免不同授权服务器实例之间互踢状态不一致。</p>
 */
public class InMemoryAuthorizationSessionRegistry implements AuthorizationSessionRegistry {

    private static final String KEY_SEPARATOR = "\u001F";

    private static final int DEFAULT_MAX_SESSIONS = 10_000;

    private final int maxSessions;

    private final ConcurrentMap<String, AuthorizationSession> sessions = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<String>> globalIndex = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<String>> clientIndex = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<String>> deviceIndex = new ConcurrentHashMap<>();

    /**
     * 创建默认容量的本地会话索引。
     */
    public InMemoryAuthorizationSessionRegistry() {
        this(DEFAULT_MAX_SESSIONS);
    }

    /**
     * 创建指定容量的本地会话索引。
     */
    public InMemoryAuthorizationSessionRegistry(int maxSessions) {
        this.maxSessions = Math.max(1, maxSessions);
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
        if (!sessions.containsKey(session.authorizationId()) && sessions.size() >= maxSessions) {
            throw new IllegalStateException(
                    "Local authorization session registry capacity exceeded, configure Redis registry or increase max-local-sessions"
            );
        }
        AuthorizationSession previous = sessions.put(session.authorizationId(), session);
        removePreviousIndexes(previous);
        globalIndex.computeIfAbsent(session.principalName(), key -> ConcurrentHashMap.newKeySet())
                .add(session.authorizationId());
        clientIndex.computeIfAbsent(clientKey(session.principalName(), session.registeredClientId()),
                        key -> ConcurrentHashMap.newKeySet())
                .add(session.authorizationId());
        if (session.deviceId() != null && !session.deviceId().isBlank()) {
            deviceIndex.computeIfAbsent(
                            deviceKey(session.principalName(), session.registeredClientId(), session.deviceId()),
                            key -> ConcurrentHashMap.newKeySet())
                    .add(session.authorizationId());
        }
    }

    /**
     * 查询用户在指定客户端下的授权会话快照。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipalAndClient(String principalName, String registeredClientId) {
        return snapshot(clientIndex.get(clientKey(principalName, registeredClientId)));
    }

    /**
     * 查询用户在指定客户端和设备下的授权会话快照。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipalClientAndDevice(
            String principalName,
            String registeredClientId,
            String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Set.of();
        }
        return snapshot(deviceIndex.get(deviceKey(principalName, registeredClientId, deviceId)));
    }

    /**
     * 查询用户全部授权会话快照。
     */
    @Override
    public Set<AuthorizationSession> findByPrincipal(String principalName) {
        return snapshot(globalIndex.get(principalName));
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
        AuthorizationSession session = sessions.remove(authorizationId);
        removeFrom(globalIndex, principalName, authorizationId);
        if (registeredClientId != null && !registeredClientId.isBlank()) {
            removeFrom(clientIndex, clientKey(principalName, registeredClientId), authorizationId);
        }
        if (session != null && session.deviceId() != null && !session.deviceId().isBlank()) {
            removeFrom(deviceIndex, deviceKey(principalName, registeredClientId, session.deviceId()), authorizationId);
        }
    }

    private void removeFrom(ConcurrentMap<String, Set<String>> index, String key, String authorizationId) {
        if (key == null || key.isBlank() || authorizationId == null || authorizationId.isBlank()) {
            return;
        }
        Set<String> authorizationIds = index.get(key);
        if (authorizationIds == null) {
            return;
        }
        authorizationIds.remove(authorizationId);
        if (authorizationIds.isEmpty()) {
            index.remove(key, authorizationIds);
        }
    }

    private void removePreviousIndexes(AuthorizationSession session) {
        if (session == null) {
            return;
        }
        removeFrom(globalIndex, session.principalName(), session.authorizationId());
        removeFrom(clientIndex, clientKey(session.principalName(), session.registeredClientId()), session.authorizationId());
        if (session.deviceId() != null && !session.deviceId().isBlank()) {
            removeFrom(deviceIndex, deviceKey(session.principalName(), session.registeredClientId(), session.deviceId()),
                    session.authorizationId());
        }
    }

    private Set<AuthorizationSession> snapshot(Set<String> authorizationIds) {
        if (authorizationIds == null || authorizationIds.isEmpty()) {
            return Set.of();
        }
        Set<AuthorizationSession> result = new LinkedHashSet<>();
        authorizationIds.stream()
                .map(sessions::get)
                .filter(java.util.Objects::nonNull)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private String clientKey(String principalName, String registeredClientId) {
        return principalName + KEY_SEPARATOR + registeredClientId;
    }

    private String deviceKey(String principalName, String registeredClientId, String deviceId) {
        return principalName + KEY_SEPARATOR + registeredClientId + KEY_SEPARATOR + deviceId;
    }
}
