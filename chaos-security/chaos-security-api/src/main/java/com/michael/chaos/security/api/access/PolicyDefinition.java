package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Set;

/**
 * 配置形态的授权策略定义。
 *
 * <p>是配置文件、数据库、配置中心与 {@link AuthorizationPolicy} 之间的中立数据结构，
 * 由 {@link AccessPolicyFactory} 转换为可执行策略。</p>
 *
 * @param id 策略 ID，出现在决策原因中，便于排查
 * @param description 策略说明
 * @param effect 命中后的效果，默认 ALLOW
 * @param actions 适用的动作编码，为空表示全部动作
 * @param conditions 属性条件，全部满足才算命中
 * @param enabled 是否启用
 */
public record PolicyDefinition(
        String id,
        String description,
        AccessEffect effect,
        Set<String> actions,
        List<ConditionDefinition> conditions,
        boolean enabled
) {

    /**
     * 规范化策略定义。
     */
    public PolicyDefinition {
        id = AccessCollections.normalizeString(id);
        description = AccessCollections.normalizeString(description);
        effect = effect == null || effect == AccessEffect.ABSTAIN ? AccessEffect.ALLOW : effect;
        actions = AccessCollections.copyStrings(actions);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    /**
     * 创建启用状态的允许策略定义。
     */
    public static PolicyDefinition allow(String id, Set<String> actions, List<ConditionDefinition> conditions) {
        return new PolicyDefinition(id, "", AccessEffect.ALLOW, actions, conditions, true);
    }

    /**
     * 创建启用状态的拒绝策略定义。
     */
    public static PolicyDefinition deny(String id, Set<String> actions, List<ConditionDefinition> conditions) {
        return new PolicyDefinition(id, "", AccessEffect.DENY, actions, conditions, true);
    }
}
