package com.michael.chaos.authorization.kickout;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.security.api.auth.LoginUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 登录互踢服务。
 */
public interface AuthorizationKickoutService {

    /**
     * 对一次成功登录应用已配置的互踢策略。
     *
     * @param user 已认证用户
     * @param registeredClient token 请求使用的 OAuth2 客户端
     */
    default void kickout(LoginUser user, RegisteredClient registeredClient) {
        kickout(user, registeredClient, AuthorizationLoginContext.empty());
    }

    /**
     * 对一次成功登录应用已配置的互踢策略。
     *
     * @param user 已认证用户
     * @param registeredClient token 请求使用的 OAuth2 客户端
     * @param loginContext 登录上下文
     */
    void kickout(LoginUser user, RegisteredClient registeredClient, AuthorizationLoginContext loginContext);

    /**
     * 记录新签发授权。
     *
     * <p>默认不处理，只有需要维护会话索引的实现才覆盖该方法。</p>
     *
     * @param user 已认证用户
     * @param registeredClient token 请求使用的 OAuth2 客户端
     * @param authorization 新保存的授权对象
     */
    default void record(
            LoginUser user,
            RegisteredClient registeredClient,
            OAuth2Authorization authorization,
            AuthorizationLoginContext loginContext) {
    }
}
