package com.michael.chaos.authorization.grant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

/**
 * 自定义 grant_type 认证请求。
 */
public class ChaosGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    private final Map<String, Object> additionalParameters;

    /**
     * 创建自定义 grant_type 认证请求。
     */
    public ChaosGrantAuthenticationToken(
            AuthorizationGrantType authorizationGrantType,
            Authentication clientPrincipal,
            Map<String, Object> additionalParameters) {
        super(authorizationGrantType, clientPrincipal, additionalParameters);
        this.additionalParameters = Collections.unmodifiableMap(new LinkedHashMap<>(additionalParameters));
    }

    /**
     * 返回不可变附加请求参数。
     */
    @Override
    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }
}
