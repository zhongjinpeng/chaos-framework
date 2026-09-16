package com.michael.chaos.security.api.access;

/**
 * 通用授权策略。
 */
public interface AuthorizationPolicy {

    /**
     * 策略 ID。
     */
    String id();

    /**
     * 执行授权决策。
     */
    AuthorizationDecision decide(AuthorizationRequest request);
}
