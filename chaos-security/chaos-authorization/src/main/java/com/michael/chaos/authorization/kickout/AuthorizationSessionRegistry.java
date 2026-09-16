package com.michael.chaos.authorization.kickout;

import java.util.Set;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/**
 * 授权会话索引。
 *
 * <p>Spring Authorization Server 的通用授权存储只提供按授权 ID 和 token 查询，
 * 这里额外维护用户维度索引，用于登录互踢时快速定位旧授权。</p>
 */
public interface AuthorizationSessionRegistry {

    /**
     * 保存授权会话索引。
     *
     * @param authorization 已保存的授权对象
     */
    void save(OAuth2Authorization authorization);

    /**
     * 查询指定用户在指定 OAuth2 客户端下的授权会话。
     *
     * @param principalName 用户主体标识
     * @param registeredClientId OAuth2 客户端 ID
     * @return 授权会话集合
     */
    Set<AuthorizationSession> findByPrincipalAndClient(String principalName, String registeredClientId);

    /**
     * 查询指定用户、OAuth2 客户端和设备下的授权会话。
     *
     * @param principalName 用户主体标识
     * @param registeredClientId OAuth2 客户端 ID
     * @param deviceId 设备 ID
     * @return 授权会话集合
     */
    Set<AuthorizationSession> findByPrincipalClientAndDevice(
            String principalName,
            String registeredClientId,
            String deviceId);

    /**
     * 查询指定用户的全部授权会话。
     *
     * @param principalName 用户主体标识
     * @return 授权会话集合
     */
    Set<AuthorizationSession> findByPrincipal(String principalName);

    /**
     * 删除授权会话索引。
     *
     * @param authorization 授权对象
     */
    void remove(OAuth2Authorization authorization);

    /**
     * 删除授权会话索引。
     *
     * @param session 授权会话摘要
     */
    default void remove(AuthorizationSession session) {
        if (session == null) {
            return;
        }
        remove(session.principalName(), session.registeredClientId(), session.authorizationId());
    }

    /**
     * 删除指定授权 ID 的会话索引。
     *
     * @param principalName 用户主体标识
     * @param registeredClientId OAuth2 客户端 ID，可为空
     * @param authorizationId 授权 ID
     */
    void remove(String principalName, String registeredClientId, String authorizationId);
}
