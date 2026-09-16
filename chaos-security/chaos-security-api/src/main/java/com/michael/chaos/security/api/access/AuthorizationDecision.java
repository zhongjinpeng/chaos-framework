package com.michael.chaos.security.api.access;

import java.util.Map;
import java.util.Objects;

/**
 * 授权决策。
 *
 * @param effect 决策结果
 * @param reason 决策原因
 * @param policyId 命中的策略 ID
 * @param attributes 决策属性
 */
public record AuthorizationDecision(
        AccessEffect effect,
        String reason,
        String policyId,
        Map<String, Object> attributes
) {

    /**
     * 规范化决策字段。
     */
    public AuthorizationDecision {
        effect = effect == null ? AccessEffect.ABSTAIN : effect;
        reason = Objects.requireNonNullElse(reason, "").trim();
        policyId = Objects.requireNonNullElse(policyId, "").trim();
        attributes = AccessCollections.copyAttributes(attributes);
    }

    /**
     * 创建允许决策。
     */
    public static AuthorizationDecision allow(String policyId, String reason) {
        return new AuthorizationDecision(AccessEffect.ALLOW, reason, policyId, Map.of());
    }

    /**
     * 创建拒绝决策。
     */
    public static AuthorizationDecision deny(String policyId, String reason) {
        return new AuthorizationDecision(AccessEffect.DENY, reason, policyId, Map.of());
    }

    /**
     * 创建不适用决策。
     */
    public static AuthorizationDecision abstain(String reason) {
        return new AuthorizationDecision(AccessEffect.ABSTAIN, reason, "", Map.of());
    }

    /**
     * 是否允许。
     */
    public boolean allowed() {
        return effect == AccessEffect.ALLOW;
    }
}
