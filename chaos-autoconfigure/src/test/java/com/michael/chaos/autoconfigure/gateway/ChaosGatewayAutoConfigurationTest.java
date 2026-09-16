package com.michael.chaos.autoconfigure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.autoconfigure.tenant.ChaosTenantAutoConfiguration;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.gateway.filter.GatewayAccessLogFilter;
import com.michael.chaos.gateway.filter.GatewayDownstreamTimingFilter;
import com.michael.chaos.gateway.filter.GatewayFallbackExceptionHandler;
import com.michael.chaos.gateway.filter.GatewayRateLimitFilter;
import com.michael.chaos.gateway.filter.GatewayTraceFilter;
import com.michael.chaos.gateway.filter.JwtAuthenticationGatewayFilter;
import com.michael.chaos.gateway.filter.TenantGatewayFilter;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimitKeyResolver;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimiter;
import com.michael.chaos.gateway.ratelimit.RateLimiterGatewayAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import reactor.core.publisher.Mono;

/**
 * Gateway 自动装配测试。
 */
class ChaosGatewayAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ChaosTenantAutoConfiguration.class,
                    ChaosGatewayAutoConfiguration.class
            ))
            .withBean(ReactiveOpaqueTokenIntrospector.class, () -> token -> Mono.empty())
            .withPropertyValues("chaos.gateway.jwt.jwk-set-uri=http://auth-server:9000/oauth2/jwks");

    /**
     * 默认应注册 Gateway 治理过滤器。
     */
    @Test
    void shouldRegisterGatewayFilters() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosGatewayProperties.class);
            assertThat(context).hasSingleBean(GatewayAccessLogFilter.class);
            assertThat(context).hasSingleBean(GatewayDownstreamTimingFilter.class);
            assertThat(context).hasSingleBean(GatewayTraceFilter.class);
            assertThat(context).hasSingleBean(com.michael.chaos.gateway.filter.GatewayRequestSanitizeFilter.class);
            assertThat(context).hasSingleBean(GatewayRateLimiter.class);
            assertThat(context).hasSingleBean(GatewayRateLimitKeyResolver.class);
            assertThat(context).hasSingleBean(GatewayRateLimitFilter.class);
            assertThat(context).hasSingleBean(JwtAuthenticationGatewayFilter.class);
            assertThat(context).hasSingleBean(TenantGatewayFilter.class);
            assertThat(context).hasSingleBean(GatewayFallbackExceptionHandler.class);
        });
    }

    /**
     * 自动装配的 introspector 必须带缓存与超时装饰。
     */
    @Test
    void shouldWrapOpaqueTokenIntrospectorWithCacheAndTimeout() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosGatewayAutoConfiguration.class))
                .withPropertyValues(
                        "chaos.gateway.token.type=opaque",
                        "chaos.gateway.opaque-token.introspection-uri=http://auth-server:9000/oauth2/introspect",
                        "chaos.gateway.opaque-token.client-id=gateway",
                        "chaos.gateway.opaque-token.client-secret=secret")
                .run(context -> assertThat(context.getBean(ReactiveOpaqueTokenIntrospector.class))
                        .isInstanceOf(com.michael.chaos.gateway.security.CachingReactiveOpaqueTokenIntrospector.class));
    }

    /**
     * 开启 JWT 鉴权却没有解码器时阻断启动，并给出可操作的修复方式（否则上线后全部请求 401）。
     */
    @Test
    void shouldFailFastWhenJwtDecoderIsMissing() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosGatewayAutoConfiguration.class))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .isInstanceOf(ChaosDiagnosticException.class)
                        .hasMessageStartingWith("问题：网关开启了 JWT 鉴权，但没有可用的 JWT 解码器")
                        .hasMessageContaining("chaos.gateway.jwt.jwk-set-uri"));
    }

    /**
     * 网关不负责验签或关闭鉴权时不要求解码器。
     */
    @Test
    void shouldNotRequireJwtDecoderWhenValidationDisabled() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosGatewayAutoConfiguration.class))
                .withPropertyValues("chaos.gateway.jwt.validation-enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * opaque introspection 缺少凭据时给出配置项名称。
     */
    @Test
    void shouldExplainMissingOpaqueCredentials() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosGatewayAutoConfiguration.class))
                .withPropertyValues(
                        "chaos.gateway.token.type=opaque",
                        "chaos.gateway.opaque-token.introspection-uri=http://auth-server:9000/oauth2/introspect")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ChaosDiagnosticException.class)
                        .hasMessageContaining("chaos.gateway.opaque-token.client-secret"));
    }

    @Test
    void shouldDisableRequestTimingWithTheSingleSwitch() {
        contextRunner.withPropertyValues("chaos.gateway.request-timing-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GatewayAccessLogFilter.class);
                    assertThat(context).doesNotHaveBean(GatewayDownstreamTimingFilter.class);
                });
    }

    /**
     * 业务自定义同类型 Bean 时应覆盖默认过滤器。
     */
    @Test
    void shouldBackOffWhenCustomFilterProvided() {
        contextRunner.withUserConfiguration(CustomFilterConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(GatewayTraceFilter.class));
    }

    /**
     * 默认白名单只应包含健康检查，避免默认暴露 Prometheus 指标。
     */
    @Test
    void whitelistShouldOnlyExposeHealthByDefault() {
        contextRunner.run(context -> {
            ChaosGatewayProperties properties = context.getBean(ChaosGatewayProperties.class);
            assertThat(properties.getWhitelist()).containsExactly("/actuator/health");
            assertThat(properties.getToken().getType()).isEqualTo(ChaosGatewayProperties.TokenType.JWT);
            assertThat(properties.getRateLimit().getKeyTypes()).containsExactly(
                    ChaosGatewayProperties.KeyType.ROUTE,
                    ChaosGatewayProperties.KeyType.TENANT,
                    ChaosGatewayProperties.KeyType.USER,
                    ChaosGatewayProperties.KeyType.IP
            );
        });
    }

    /**
     * 生产模式发现本地内存 Gateway 限流器时默认阻断启动，多实例下单机计数无法提供全局限流。
     */
    @Test
    void shouldRejectLocalGatewayRateLimiterInProductionMode() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 显式关闭 fail-fast 后只告警，保留灰度迁移期间的逃生开关。
     */
    @Test
    void shouldOnlyWarnAboutLocalGatewayRateLimiterWhenFailFastDisabled() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.fail-fast=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GatewayRateLimiter.class))
                            .isInstanceOf(RateLimiterGatewayAdapter.class);
                    assertThat(context.getBean(RateLimiter.class)).isInstanceOf(InMemoryRateLimiter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterConfiguration {

        /**
         * 自定义 Gateway trace 过滤器。
         */
        @Bean
        GatewayTraceFilter gatewayTraceFilter() {
            return new GatewayTraceFilter();
        }
    }
}
