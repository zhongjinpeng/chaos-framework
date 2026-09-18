package com.michael.chaos.security.api.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.auth.LoginUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 配置形态策略转换测试。
 */
class AccessPolicyFactoryTest {

    private static final String PATH = "chaos.security.access.policies";

    /**
     * 同租户才能读订单：属性对属性比较应可从配置表达。
     */
    @Test
    void shouldCreateExecutablePolicyFromDefinition() {
        AuthorizationPolicy policy = AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "same-tenant",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of("subject.tenantId", AttributeOperator.EQ, "resource.tenantId"))),
                PATH + "[0]");

        AccessSubject subject = AccessSubject.from(
                new LoginUser("1", "alice", "tenant-a", Set.of("user"), Set.of()));

        assertThat(policy.id()).isEqualTo("same-tenant");
        assertThat(policy.decide(AuthorizationRequest.of(
                subject,
                "order:read",
                AuthorizationResource.of("order", "1", Map.of("tenantId", "tenant-a")))).allowed()).isTrue();
        assertThat(policy.decide(AuthorizationRequest.of(
                subject,
                "order:read",
                AuthorizationResource.of("order", "2", Map.of("tenantId", "tenant-b")))).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
        assertThat(policy.decide(AuthorizationRequest.of(subject, "order:delete")).effect())
                .isEqualTo(AccessEffect.ABSTAIN);
    }

    /**
     * 拒绝策略与禁用策略应分别生效与跳过。
     */
    @Test
    void shouldSupportDenyEffectAndSkipDisabledPolicies() {
        List<AuthorizationPolicy> policies = AccessPolicyFactory.create(
                List.of(
                        PolicyDefinition.deny(
                                "deny-external",
                                Set.of("order:read"),
                                List.of(ConditionDefinition.of(
                                        "environment.network",
                                        AttributeOperator.EQ,
                                        Set.of("external")))),
                        new PolicyDefinition(
                                "disabled",
                                "",
                                AccessEffect.ALLOW,
                                Set.of("order:read"),
                                List.of(),
                                false)),
                PATH);

        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).decide(AuthorizationRequest.of(
                AccessSubject.ANONYMOUS,
                "order:read",
                AuthorizationResource.NONE,
                Map.of("network", "external"))).effect()).isEqualTo(AccessEffect.DENY);
    }

    /**
     * 空定义列表应返回空策略列表。
     */
    @Test
    void shouldReturnEmptyForNoDefinitions() {
        assertThat(AccessPolicyFactory.create(List.of(), PATH)).isEmpty();
        assertThat(AccessPolicyFactory.create((List<PolicyDefinition>) null, PATH)).isEmpty();
    }

    /**
     * 缺少 ID 时应给出可操作的诊断。
     */
    @Test
    void shouldRejectBlankId() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(" ", Set.of("order:read"), List.of()),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("缺少 ID")
                .hasMessageContaining(PATH + "[0].id");
    }

    /**
     * 既无动作也无条件的策略会对所有请求生效，应拒绝启动。
     */
    @Test
    void shouldRejectPolicyWithoutActionsAndConditions() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow("catch-all", Set.of(), List.of()),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("既没有动作也没有条件")
                .hasMessageContaining(PATH + "[0].actions");
    }

    /**
     * 需要比较值的操作符缺少 values 时应拒绝启动。
     */
    @Test
    void shouldRejectMissingComparisonValue() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of("subject.tenantId", AttributeOperator.EQ, Set.of()))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("缺少比较值")
                .hasMessageContaining(PATH + "[0].conditions[0].values");
    }

    /**
     * EXISTS 不需要比较值，配了反而说明理解有误。
     */
    @Test
    void shouldRejectRedundantValueForExists() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of("subject.userId", AttributeOperator.EXISTS, Set.of("1")))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("不需要比较值");
    }

    /**
     * IN 不支持属性对属性比较。
     */
    @Test
    void shouldRejectReferenceForUnsupportedOperator() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of("subject.roles", AttributeOperator.IN, "resource.roles"))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("不支持属性对属性比较");
    }

    /**
     * BETWEEN 必须恰好两个边界值。
     */
    @Test
    void shouldRejectBetweenWithWrongValueCount() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of(
                                "environment.hour",
                                AttributeOperator.BETWEEN,
                                Set.of("09:00")))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("BETWEEN 条件需要 2 个比较值");
    }

    /**
     * 非法正则应在启动期暴露。
     */
    @Test
    void shouldRejectInvalidRegex() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of("environment.clientIp", AttributeOperator.REGEX, Set.of("[")))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("正则表达式非法");
    }

    /**
     * 策略 ID 重复时无法定位决策来源，应拒绝启动。
     */
    @Test
    void shouldRejectDuplicatedId() {
        PolicyDefinition definition = PolicyDefinition.allow("same", Set.of("order:read"), List.of());

        assertThatThrownBy(() -> AccessPolicyFactory.create(List.of(definition, definition), PATH))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("ID 重复");
    }

    /**
     * 属性引用为空时应提示可用的引用写法。
     */
    @Test
    void shouldRejectBlankAttributeReference() {
        assertThatThrownBy(() -> AccessPolicyFactory.create(
                PolicyDefinition.allow(
                        "bad",
                        Set.of("order:read"),
                        List.of(ConditionDefinition.of(" ", AttributeOperator.EQ, Set.of("x")))),
                PATH + "[0]"))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("subject.tenantId");
    }
}
