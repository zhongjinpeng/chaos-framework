package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.tenant.TenantAccessValidator;
import com.michael.chaos.tenant.TenantDescriptor;
import com.michael.chaos.tenant.TenantIsolationMode;
import com.michael.chaos.tenant.TenantPlan;
import com.michael.chaos.tenant.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Gateway 租户状态治理过滤器测试。
 */
class TenantGatewayFilterTest {

    /**
     * 冻结租户应返回 403。
     */
    @Test
    void shouldRejectFrozenTenant() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        TenantAccessValidator validator = new TenantAccessValidator(tenantId -> new TenantDescriptor(
                tenantId,
                TenantStatus.FROZEN,
                TenantPlan.empty(),
                TenantIsolationMode.SHARED_SCHEMA
        ), true);
        TenantGatewayFilter filter = new TenantGatewayFilter(properties, validator);
        MockServerWebExchange exchange = exchange("/api/orders", "tenant-a");

        StepVerifier.create(filter.filter(exchange, chain -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"403\"");
    }

    /**
     * 正常租户应继续透传给下游。
     */
    @Test
    void shouldAllowActiveTenant() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        TenantAccessValidator validator = new TenantAccessValidator(TenantDescriptor::active, true);
        TenantGatewayFilter filter = new TenantGatewayFilter(properties, validator);
        MockServerWebExchange exchange = exchange("/api/orders", "tenant-a");

        StepVerifier.create(filter.filter(exchange, chain -> {
            assertThat(exchange.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("tenant-a");
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 白名单路径应跳过租户状态校验。
     */
    @Test
    void shouldSkipWhitelist() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        TenantAccessValidator validator = new TenantAccessValidator(tenantId -> TenantDescriptor.unknown(tenantId), true);
        TenantGatewayFilter filter = new TenantGatewayFilter(properties, validator);
        MockServerWebExchange exchange = exchange("/actuator/health", "");

        StepVerifier.create(filter.filter(exchange, chain -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 已认证租户与客户端自定义租户头不一致时必须返回 403，防止跨租户冒充。
     */
    @Test
    void shouldRejectTenantMismatchWithAuthenticatedTenant() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getTenant().setHeaderName("X-Tenant-Code");
        TenantAccessValidator validator = new TenantAccessValidator(TenantDescriptor::active, true);
        TenantGatewayFilter filter = new TenantGatewayFilter(properties, validator);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders")
                .header("X-Tenant-Code", "tenant-b"));
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, "tenant-a");

        StepVerifier.create(filter.filter(exchange, chain -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 已认证请求未显式声明租户时应使用 token 中的租户。
     */
    @Test
    void shouldUseAuthenticatedTenantWhenHeaderMissing() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        TenantAccessValidator validator = new TenantAccessValidator(TenantDescriptor::active, true);
        TenantGatewayFilter filter = new TenantGatewayFilter(properties, validator);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, "tenant-a");

        StepVerifier.create(filter.filter(exchange, next -> {
            assertThat(next.getRequest().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("tenant-a");
            return Mono.empty();
        })).verifyComplete();
    }

    private MockServerWebExchange exchange(String path, String tenantId) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header("X-Forwarded-For", "10.0.0.1")
                .header("X-Tenant-Id", tenantId));
    }
}
