package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 策略 JSON 编解码测试。
 */
class AuthorizationPolicyJsonCodecTest {

    private final AuthorizationPolicyJsonCodec codec = new AuthorizationPolicyJsonCodec();

    /**
     * 应解析策略数组，枚举支持 NOT_EQ / not-eq / notEq 三种写法。
     */
    @Test
    void shouldDecodePolicyArrayWithRelaxedEnums() {
        String json = """
                [
                  {
                    "id": "order-owner-only",
                    "description": "只能修改自己的订单",
                    "effect": "deny",
                    "actions": ["order:update"],
                    "conditions": [
                      {"left": "resource.ownerId", "operator": "not-eq", "right": "subject.userId"}
                    ]
                  },
                  {
                    "id": "deny-large-amount",
                    "effect": "DENY",
                    "actions": ["order:update"],
                    "enabled": false,
                    "conditions": [
                      {"left": "resource.amount", "operator": "gte", "values": ["100000"]}
                    ]
                  }
                ]
                """;

        List<PolicyDefinition> definitions = codec.decode(json, "测试");

        assertThat(definitions).hasSize(2);
        assertThat(definitions.getFirst().id()).isEqualTo("order-owner-only");
        assertThat(definitions.getFirst().effect()).isEqualTo(AccessEffect.DENY);
        assertThat(definitions.getFirst().conditions().getFirst().operator()).isEqualTo(AttributeOperator.NOT_EQ);
        assertThat(definitions.get(1).enabled()).isFalse();
        assertThat(definitions.get(1).conditions().getFirst().operator()).isEqualTo(AttributeOperator.GTE);
    }

    /**
     * 单个 JSON 对象也应能解析。
     */
    @Test
    void shouldDecodeSinglePolicyObject() {
        List<PolicyDefinition> definitions = codec.decode(
                "{\"id\":\"p1\",\"actions\":[\"order:read\"],\"conditions\":[{\"left\":\"subject.userId\","
                        + "\"operator\":\"exists\"}]}",
                "测试");

        assertThat(definitions).hasSize(1);
        assertThat(definitions.getFirst().effect()).isEqualTo(AccessEffect.ALLOW);
    }

    /**
     * 没写 enabled 时应按启用处理：record 的 boolean 默认 false 会让整条策略静默失效。
     */
    @Test
    void missingEnabledShouldDefaultToTrue() {
        List<PolicyDefinition> definitions = codec.decode(
                "[{\"id\":\"p1\",\"actions\":[\"order:read\"]}]",
                "测试");

        assertThat(definitions.getFirst().enabled()).isTrue();
    }

    /**
     * 空内容返回空列表。
     */
    @Test
    void shouldDecodeBlankAsEmptyList() {
        assertThat(codec.decode(null, "测试")).isEmpty();
        assertThat(codec.decode("  ", "测试")).isEmpty();
    }

    /**
     * 编码后应能原样解码回来。
     */
    @Test
    void shouldRoundTrip() {
        List<PolicyDefinition> definitions = List.of(PolicyDefinition.deny(
                "deny-external",
                Set.of("order:read"),
                List.of(ConditionDefinition.of("environment.network", AttributeOperator.EQ, Set.of("external")))));

        assertThat(codec.decode(codec.encode(definitions), "测试")).isEqualTo(definitions);
        assertThat(codec.encode(null)).isEqualTo("[]");
    }

    /**
     * JSON 非法时给出可操作诊断，并指出来源。
     */
    @Test
    void shouldFailWithDiagnosticForInvalidJson() {
        assertThatThrownBy(() -> codec.decode("[{", "Redis key chaos:security:access:policies"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("授权策略 JSON 无法解析")
                .hasMessageContaining("Redis key chaos:security:access:policies");
    }

    /**
     * 枚举写错时同样落到诊断异常，而不是抛出 Jackson 原始异常。
     */
    @Test
    void shouldFailWithDiagnosticForUnknownEnum() {
        assertThatThrownBy(() -> codec.decode(
                "[{\"id\":\"p1\",\"actions\":[\"order:read\"],\"conditions\":[{\"left\":\"subject.userId\","
                        + "\"operator\":\"matches\"}]}]",
                "测试"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("AttributeOperator 不支持的取值：matches");
    }
}
