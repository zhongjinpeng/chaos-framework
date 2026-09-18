package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 授权策略配置转换测试。
 */
class AccessPolicyPropertiesTest {

    /**
     * 配置对象应完整转换为策略定义。
     */
    @Test
    void shouldConvertToDefinition() {
        AccessConditionProperties condition = new AccessConditionProperties();
        condition.setLeft("resource.ownerId");
        condition.setOperator(AttributeOperator.NOT_EQ);
        condition.setRight("subject.userId");

        AccessPolicyProperties policy = new AccessPolicyProperties();
        policy.setId("order-owner-only");
        policy.setDescription("只能操作自己的订单");
        policy.setEffect(AccessEffect.DENY);
        policy.setActions(List.of("order:update"));
        policy.setConditions(List.of(condition));

        PolicyDefinition definition = policy.toDefinition();

        assertThat(definition.id()).isEqualTo("order-owner-only");
        assertThat(definition.description()).isEqualTo("只能操作自己的订单");
        assertThat(definition.effect()).isEqualTo(AccessEffect.DENY);
        assertThat(definition.actions()).containsExactly("order:update");
        assertThat(definition.enabled()).isTrue();
        assertThat(definition.conditions()).hasSize(1);
        assertThat(definition.conditions().getFirst().left()).isEqualTo("resource.ownerId");
        assertThat(definition.conditions().getFirst().operator()).isEqualTo(AttributeOperator.NOT_EQ);
        assertThat(definition.conditions().getFirst().right()).isEqualTo("subject.userId");
    }

    /**
     * 默认值：效果为 ALLOW、启用、操作符为 EQ。
     */
    @Test
    void shouldApplyDefaults() {
        AccessPolicyProperties policy = new AccessPolicyProperties();
        policy.setId("default");
        policy.setEffect(null);
        policy.setActions(null);
        policy.setConditions(null);

        PolicyDefinition definition = policy.toDefinition();

        assertThat(definition.effect()).isEqualTo(AccessEffect.ALLOW);
        assertThat(definition.actions()).isEmpty();
        assertThat(definition.conditions()).isEmpty();
        assertThat(new AccessConditionProperties().toDefinition().operator()).isEqualTo(AttributeOperator.EQ);
    }

}
