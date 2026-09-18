package com.michael.chaos.gateway.filter;

import static com.michael.chaos.gateway.filter.GatewayFilterTestSupport.responseBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimitKeyResolver;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimiter;
import com.michael.chaos.gateway.ratelimit.RateLimiterGatewayAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Gateway 全局限流过滤器测试。
 */
class GatewayRateLimitFilterTest {

    /**
     * 超过默认每秒许可数时应返回 429。
     */
    @Test
    void shouldRejectWhenRateLimitExceeded() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setDefaultPermitsPerSecond(1);
        GatewayRateLimitFilter filter = filter(properties, new RateLimiterGatewayAdapter(new InMemoryRateLimiter(), Schedulers.immediate()));
        MockServerWebExchange first = exchange("/api/orders");
        MockServerWebExchange second = exchange("/api/orders");

        StepVerifier.create(filter.filter(first, next -> Mono.empty())).verifyComplete();
        StepVerifier.create(filter.filter(second, next -> Mono.empty())).verifyComplete();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(responseBody(second)).contains("\"code\":\"429\"");
    }

    /**
     * skip-paths 中的路径不参与限流。
     */
    @Test
    void shouldSkipWhitelist() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setDefaultPermitsPerSecond(1);
        GatewayRateLimitFilter filter = filter(properties, new RateLimiterGatewayAdapter(new InMemoryRateLimiter(), Schedulers.immediate()));
        MockServerWebExchange exchange = exchange("/actuator/health");

        StepVerifier.create(filter.filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 鉴权白名单中的登录接口仍然必须参与限流。
     */
    @Test
    void shouldRateLimitAuthWhitelistedPath() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.setWhitelist(List.of("/api/v1/auth/**"));
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setDefaultPermitsPerSecond(1);
        GatewayRateLimitFilter filter = filter(properties, new RateLimiterGatewayAdapter(new InMemoryRateLimiter(), Schedulers.immediate()));
        MockServerWebExchange first = exchange("/api/v1/auth/token");
        MockServerWebExchange second = exchange("/api/v1/auth/token");

        StepVerifier.create(filter.filter(first, next -> Mono.empty())).verifyComplete();
        StepVerifier.create(filter.filter(second, next -> Mono.empty())).verifyComplete();

        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 限流器异常且 fail-open 开启时应放行。
     */
    @Test
    void shouldAllowWhenLimiterFailsAndFailOpen() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setFailOpen(true);
        GatewayRateLimitFilter filter = filter(properties, failingLimiter());
        MockServerWebExchange exchange = exchange("/api/orders");

        StepVerifier.create(filter.filter(exchange, next -> {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * 限流器异常且 fail-open 关闭时应拒绝。
     */
    @Test
    void shouldRejectWhenLimiterFailsAndFailClosed() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setFailOpen(false);
        GatewayRateLimitFilter filter = filter(properties, failingLimiter());
        MockServerWebExchange exchange = exchange("/api/orders");

        StepVerifier.create(filter.filter(exchange, next -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 路径规则应覆盖默认限流配置。
     */
    @Test
    void shouldUsePathRule() {
        ChaosGatewayProperties properties = new ChaosGatewayProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setDefaultPermitsPerSecond(100);
        ChaosGatewayProperties.Rule rule = new ChaosGatewayProperties.Rule();
        rule.setId("order-api");
        rule.setPathPattern("/api/orders/**");
        rule.setPermitsPerSecond(1);
        rule.setKeyTypes(List.of(ChaosGatewayProperties.KeyType.PATH));
        properties.getRateLimit().setRules(List.of(rule));
        GatewayRateLimitFilter filter = filter(properties, new RateLimiterGatewayAdapter(new InMemoryRateLimiter(), Schedulers.immediate()));
        MockServerWebExchange first = exchange("/api/orders/1");
        MockServerWebExchange second = exchange("/api/orders/1");

        StepVerifier.create(filter.filter(first, next -> Mono.empty())).verifyComplete();
        StepVerifier.create(filter.filter(second, next -> Mono.empty())).verifyComplete();

        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private GatewayRateLimitFilter filter(ChaosGatewayProperties properties, GatewayRateLimiter rateLimiter) {
        return new GatewayRateLimitFilter(properties, rateLimiter, new GatewayRateLimitKeyResolver());
    }

    private GatewayRateLimiter failingLimiter() {
        return context -> Mono.error(new IllegalStateException("rate limiter unavailable"));
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header("X-Forwarded-For", "10.0.0.1")
                .header("X-User-Id", "1001")
                .header("X-Tenant-Id", "tenant-a"));
    }

}
