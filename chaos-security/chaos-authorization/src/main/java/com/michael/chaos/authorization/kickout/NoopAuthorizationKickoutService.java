package com.michael.chaos.authorization.kickout;

import com.michael.chaos.authorization.session.AuthorizationLoginContext;
import com.michael.chaos.security.api.auth.LoginUser;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 空操作互踢服务。
 */
public class NoopAuthorizationKickoutService implements AuthorizationKickoutService {

    /**
     * 不执行任何授权清理。
     */
    @Override
    public void kickout(LoginUser user, RegisteredClient registeredClient, AuthorizationLoginContext loginContext) {
    }
}
