package com.michael.chaos.security.api.access;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ABAC 授权策略配置。
 *
 * <p>一条策略表示"哪些动作，在满足哪些属性条件时，允许或拒绝"；全部条件同时满足才算命中，
 * 未命中的策略弃权，由合并算法决定最终结果。</p>
 *
 * <p>放在 chaos-security-api 而不是各自的配置包：资源服务器（chaos-security）与网关（chaos-gateway）
 * 都要绑定同一套策略配置，网关只能看到 chaos-security-api。本类是普通 JavaBean，不依赖 Spring。</p>
 */
public class AccessPolicyProperties {

    /**
     * 策略 ID，会出现在拒绝原因与审计事件里，必须唯一。
     */
    private String id;

    /**
     * 策略说明。
     */
    private String description;

    /**
     * 命中后的效果。
     */
    private AccessEffect effect = AccessEffect.ALLOW;

    /**
     * 适用的动作编码，留空表示全部动作。
     */
    private List<String> actions = List.of();

    /**
     * 属性条件，全部满足才算命中。
     */
    private List<AccessConditionProperties> conditions = List.of();

    /**
     * 是否启用。
     */
    private boolean enabled = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AccessEffect getEffect() {
        return effect;
    }

    public void setEffect(AccessEffect effect) {
        this.effect = effect == null ? AccessEffect.ALLOW : effect;
    }

    public List<String> getActions() {
        return List.copyOf(actions);
    }

    public void setActions(List<String> actions) {
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public List<AccessConditionProperties> getConditions() {
        return List.copyOf(conditions);
    }

    public void setConditions(List<AccessConditionProperties> conditions) {
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 转换为框架内部的策略定义。
     */
    public PolicyDefinition toDefinition() {
        Set<String> copiedActions = new LinkedHashSet<>(actions);
        List<ConditionDefinition> definitions = conditions.stream()
                .filter(condition -> condition != null)
                .map(AccessConditionProperties::toDefinition)
                .toList();
        return new PolicyDefinition(id, description, effect, copiedActions, definitions, enabled);
    }
}
