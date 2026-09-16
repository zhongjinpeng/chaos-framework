package com.michael.chaos.security.api.access;

/**
 * 通用授权决策服务。
 */
public interface AuthorizationManager {

    /**
     * 对请求做最终授权决策。
     */
    AuthorizationDecision decide(AuthorizationRequest request);

    /**
     * 判断是否允许访问。
     */
    default boolean isAllowed(AuthorizationRequest request) {
        return decide(request).allowed();
    }
}
