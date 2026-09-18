package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.audit.AuditEvent;
import com.michael.chaos.test.audit.CapturingAuditEventPublisher;
import com.michael.chaos.gateway.config.AccessRuleProperties;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.security.api.access.AccessEffect;
import com.michael.chaos.security.api.access.AccessPolicyProperties;
import com.michael.chaos.security.api.access.AccessConditionProperties;
import com.michael.chaos.security.api.access.AttributeOperator;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationManagerBuilder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Gateway 粗粒度鉴权过滤器测试。
 */
class AccessControlGatewayFilterTest {

    private final CapturingAuditEventPublisher publisher = new CapturingAuditEventPublisher();

    /**
     * 命中规则且 token 带有对应权限时放行。
     */
    @Test
    void shouldAllowWhenSubjectHasPermission() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of("GET"), "order:read"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());
        authenticate(exchange, Set.of("user"), Set.of("order:read"));

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(publisher.events()).isEmpty();
    }

    /**
     * 缺少权限时返回 403，并写审计事件。
     */
    @Test
    void shouldRejectWhenSubjectLacksPermission() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of("GET"), "order:read"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());
        authenticate(exchange, Set.of("user"), Set.of("order:write"));

        StepVerifier.create(filter(properties).filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publisher.events()).hasSize(1);
        AuditEvent event = publisher.events().getFirst();
        assertThat(event.action()).isEqualTo("security.permission.denied");
        assertThat(event.principalId()).isEqualTo("1001");
        assertThat(event.attributes()).containsEntry("action", "order:read");
    }

    /**
     * 未认证请求同样被拒绝：匿名主体不会命中任何 RBAC 规则。
     */
    @Test
    void shouldRejectAnonymousRequest() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of(), "order:read"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());

        StepVerifier.create(filter(properties).filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 方法不匹配的规则不生效，请求直接放行。
     */
    @Test
    void shouldIgnoreRuleWithDifferentMethod() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of("DELETE"), "order:delete"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 没有任何规则命中时放行：网关规则是额外加固，不该因为漏配把站点挡死。
     */
    @Test
    void shouldPassThroughWhenNoRuleMatches() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of(), "order:read"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products/1").build());

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 白名单路径不做鉴权。
     */
    @Test
    void shouldSkipWhitelistedPath() {
        ChaosGatewayProperties properties = properties(rule("/actuator/**", List.of(), "actuator:read"));
        properties.setWhitelist(List.of("/actuator/health"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 关闭开关后过滤器完全不介入。
     */
    @Test
    void shouldDoNothingWhenDisabled() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of(), "order:read"));
        properties.getAccess().setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * ABAC 策略可以用 environment.http.method、clientIp 表达"外网只读"这类规则。
     */
    @Test
    void shouldApplyAbacPolicyOnHttpEnvironment() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of(), "order:write"));
        AccessConditionProperties condition = new AccessConditionProperties();
        condition.setLeft("environment.http.method");
        condition.setOperator(AttributeOperator.IN);
        condition.setValues(List.of("POST", "PUT", "DELETE"));
        AccessPolicyProperties policy = new AccessPolicyProperties();
        policy.setId("deny-write-from-external");
        policy.setEffect(AccessEffect.DENY);
        policy.setActions(List.of("order:write"));
        policy.setConditions(List.of(condition));
        properties.getAccess().setPolicies(List.of(policy));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/orders").build());
        authenticate(exchange, Set.of("user"), Set.of("order:write"));

        StepVerifier.create(filter(properties).filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publisher.events().getFirst().attributes())
                .containsEntry("policy", "deny-write-from-external");
    }

    /**
     * admin 角色直接放行，便于运维接口不逐条配权限。
     */
    @Test
    void shouldAllowAdminRole() {
        ChaosGatewayProperties properties = properties(rule("/api/orders/**", List.of(), "order:read"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/1").build());
        authenticate(exchange, Set.of("admin"), Set.of());

        StepVerifier.create(filter(properties).filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private AccessControlGatewayFilter filter(ChaosGatewayProperties properties) {
        ChaosGatewayProperties.Access access = properties.getAccess();
        AuthorizationManager manager = AuthorizationManagerBuilder.create()
                .policyGroupId("chaos-gateway-access")
                .adminRoles(access.getAdminRoles())
                .roleHierarchy(access.getRoleHierarchy())
                .wildcardPermissionEnabled(access.isWildcardPermissionEnabled())
                .combiningAlgorithm(access.getCombiningAlgorithm())
                .policyDefinitions(access.toPolicyDefinitions(), "chaos.gateway.access.policies")
                .build();
        return new AccessControlGatewayFilter(properties, manager, publisher, null);
    }

    private ChaosGatewayProperties properties(AccessRuleProperties... rules) {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getAccess().setEnabled(true);
        properties.getAccess().setRules(List.of(rules));
        return properties;
    }

    private AccessRuleProperties rule(String path, List<String> methods, String action) {
        AccessRuleProperties rule = new AccessRuleProperties();
        rule.setPath(path);
        rule.setMethods(methods);
        rule.setAction(action);
        rule.setResourceType("order");
        return rule;
    }


    private void authenticate(MockServerWebExchange exchange, Set<String> roles, Set<String> permissions) {
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_USER_ID, "1001");
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, "tenant-a");
        GatewayExchangeAttributes.putAuthenticatedAuthorities(exchange, roles, permissions);
    }

}
