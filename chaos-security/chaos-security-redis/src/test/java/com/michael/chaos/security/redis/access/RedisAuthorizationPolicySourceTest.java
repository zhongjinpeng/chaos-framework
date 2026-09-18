package com.michael.chaos.security.redis.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.access.AccessEffect;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AttributeOperator;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.AuthorizationResource;
import com.michael.chaos.security.api.access.ConditionDefinition;
import com.michael.chaos.security.api.access.PolicyDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 策略来源测试。
 */
class RedisAuthorizationPolicySourceTest {

    /**
     * Redis 中的 JSON 策略应转换为可执行策略。
     */
    @Test
    void shouldLoadPoliciesFromRedis() {
        String json = """
                [
                  {
                    "id": "deny-external",
                    "effect": "DENY",
                    "actions": ["order:read"],
                    "conditions": [
                      {"left": "environment.network", "operator": "EQ", "values": ["external"]}
                    ]
                  }
                ]
                """;
        RedisAuthorizationPolicySource source = new RedisAuthorizationPolicySource(template(json), null, null);

        List<AuthorizationPolicy> policies = source.policies();

        assertThat(source.key()).isEqualTo(RedisAuthorizationPolicySource.DEFAULT_KEY);
        assertThat(policies).hasSize(1);
        assertThat(policies.getFirst().decide(AuthorizationRequest.of(
                AccessSubject.ANONYMOUS,
                "order:read",
                AuthorizationResource.NONE,
                Map.of("network", "external"))).effect()).isEqualTo(AccessEffect.DENY);
    }

    /**
     * key 不存在或为空时返回空策略列表，而不是报错。
     */
    @Test
    void shouldReturnEmptyWhenKeyIsMissing() {
        assertThat(new RedisAuthorizationPolicySource(template(null)).policies()).isEmpty();
        assertThat(new RedisAuthorizationPolicySource(template("  ")).policies()).isEmpty();
    }

    /**
     * 策略写错时的诊断要指出是哪个 Redis key，否则运维只能盲猜。
     */
    @Test
    void shouldReportRedisKeyInDiagnostics() {
        RedisAuthorizationPolicySource source = new RedisAuthorizationPolicySource(
                template("[{\"id\":\"bad\",\"actions\":[\"order:read\"],\"conditions\":[{\"left\":\"subject.userId\","
                        + "\"operator\":\"IN\"}]}]"),
                "chaos:test:policies",
                null);

        assertThatThrownBy(source::policies)
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("缺少比较值")
                .hasMessageContaining("Redis key chaos:test:policies 中的策略[0]");
    }

    /**
     * JSON 本身非法时同样给出带 key 的诊断。
     */
    @Test
    void shouldReportInvalidJson() {
        RedisAuthorizationPolicySource source =
                new RedisAuthorizationPolicySource(template("not-json"), "chaos:test:policies", null);

        assertThatThrownBy(source::policies)
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageContaining("Redis key chaos:test:policies");
    }

    /**
     * save 便于运维脚本与测试把策略写回 Redis。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSavePoliciesAsJson() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        RedisAuthorizationPolicySource source =
                new RedisAuthorizationPolicySource(template, "chaos:test:policies", null);

        source.save(List.of(PolicyDefinition.deny(
                "deny-external",
                Set.of("order:read"),
                List.of(ConditionDefinition.of("environment.network", AttributeOperator.EQ, Set.of("external"))))));

        verify(operations).set(
                org.mockito.ArgumentMatchers.eq("chaos:test:policies"),
                org.mockito.ArgumentMatchers.contains("deny-external"));
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate template(String json) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get(anyString())).thenReturn(json);
        return template;
    }
}
