package com.michael.chaos.autoconfigure.gateway;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.autoconfigure.support.ProductionSafetyEnforcer;
import com.michael.chaos.autoconfigure.support.UnsafeForProduction;
import com.michael.chaos.autoconfigure.tenant.ChaosTenantAutoConfiguration;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.gateway.filter.AccessControlGatewayFilter;
import com.michael.chaos.gateway.filter.BlacklistFilter;
import com.michael.chaos.gateway.filter.GatewayAccessLogFilter;
import com.michael.chaos.gateway.filter.GatewayDownstreamTimingFilter;
import com.michael.chaos.gateway.filter.GatewayFallbackExceptionHandler;
import com.michael.chaos.gateway.filter.GatewayRateLimitFilter;
import com.michael.chaos.gateway.filter.GatewayRequestSanitizeFilter;
import com.michael.chaos.gateway.filter.GatewayTraceFilter;
import com.michael.chaos.gateway.filter.GrayTagFilter;
import com.michael.chaos.gateway.filter.JwtAuthenticationGatewayFilter;
import com.michael.chaos.gateway.filter.OpaqueTokenAuthenticationGatewayFilter;
import com.michael.chaos.gateway.filter.TenantGatewayFilter;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimitKeyResolver;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimiter;
import com.michael.chaos.gateway.ratelimit.RateLimiterGatewayAdapter;
import com.michael.chaos.gateway.security.CachingReactiveOpaqueTokenIntrospector;
import com.michael.chaos.gateway.security.GatewayJwtDecoders;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationManagerBuilder;
import com.michael.chaos.security.api.access.AuthorizationPolicySource;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.tenant.TenantAccessValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.NimbusReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Gateway starter 自动装配。
 */
@AutoConfiguration(afterName = "com.michael.chaos.autoconfigure.tenant.ChaosTenantAutoConfiguration")
@ConditionalOnClass(value = GlobalFilter.class, name = {
        "com.michael.chaos.gateway.config.ChaosGatewayProperties",
        // 网关鉴权过滤器与安全过滤器链直接依赖 Spring Security 响应式 OAuth2 资源服务器。
        "org.springframework.security.config.web.server.ServerHttpSecurity",
        "org.springframework.security.oauth2.jwt.ReactiveJwtDecoder",
        "org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector"
})
@EnableWebFluxSecurity
// @EnableWebFluxSecurity 与 Servlet 安全配置冲突，网关治理只在响应式应用中装配。
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(ChaosGatewayProperties.class)
public class ChaosGatewayAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaosGatewayAutoConfiguration.class);

    /**
     * 禁用 Spring Security 默认拦截，认证由 Chaos Gateway Filter 处理。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain gatewaySecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    /**
     * 注册 Gateway trace 过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayTraceFilter gatewayTraceFilter() {
        return new GatewayTraceFilter();
    }

    /**
     * 注册入站请求净化过滤器：剔除内部身份请求头、拒绝歧义路径、按可信代理解析客户端 IP。
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRequestSanitizeFilter gatewayRequestSanitizeFilter(ChaosGatewayProperties properties) {
        return new GatewayRequestSanitizeFilter(properties);
    }

    /**
     * 注册 IP 黑名单过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public BlacklistFilter blacklistFilter(
            ChaosGatewayProperties properties,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        return new BlacklistFilter(
                properties,
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new)
        );
    }

    /**
     * 注册网关默认本地限流器。
     *
     * <p>复用 chaos-core 的 {@link InMemoryRateLimiter}（滑动窗口 + LRU 淘汰），只在容器中没有任何
     * {@link RateLimiter} 时注册：引入 chaos-redis-starter 时 Redis 集群限流会先行注册并被网关直接复用。</p>
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    @UnsafeForProduction(value = InMemoryRateLimiter.class, message = "chaos-gateway: 生产环境不能使用 InMemoryRateLimiter，请引入 chaos-redis-starter 或接入分布式限流实现")
    public RateLimiter gatewayLocalRateLimiter(ChaosGatewayProperties properties) {
        return new InMemoryRateLimiter(properties.getRateLimit().getMaxLocalKeys());
    }

    /**
     * 注册默认 Gateway 响应式限流端口。
     *
     * <p>内存实现不阻塞，直接在当前线程执行；其他实现（Redis 等）默认视为阻塞调用，切换到 boundedElastic，
     * 避免阻塞 Netty 事件循环。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRateLimiter gatewayRateLimiter(RateLimiter rateLimiter) {
        Scheduler scheduler = rateLimiter instanceof InMemoryRateLimiter
                ? Schedulers.immediate()
                : Schedulers.boundedElastic();
        return new RateLimiterGatewayAdapter(rateLimiter, scheduler);
    }

    /**
     * 注册生产安全检查器。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosGatewayProductionSafetyChecker")
    public SmartInitializingSingleton chaosGatewayProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        return new ProductionSafetyEnforcer(environment, beanFactory);
    }

    /**
     * 启动时校验 JWT 鉴权所需的解码器。
     *
     * <p>开启鉴权、token 类型为 JWT 且开启网关验签时，如果既没有配置 {@code chaos.gateway.jwt.jwk-set-uri}
     * 也没有自定义 {@link ReactiveJwtDecoder}，网关会对所有需要鉴权的请求返回 401（"jwt decoder is missing"）。
     * 这类问题在启动时就能确定，直接阻断启动并给出修复方式，比上线后全站 401 更容易排查。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosGatewayJwtDecoderVerifier")
    public SmartInitializingSingleton chaosGatewayJwtDecoderVerifier(
            ChaosGatewayProperties properties,
            ObjectProvider<ReactiveJwtDecoder> jwtDecoderProvider) {
        return () -> {
            if (properties.isAuthEnabled()
                    && properties.getToken().getType() == ChaosGatewayProperties.TokenType.JWT
                    && properties.getJwt().isValidationEnabled()
                    && jwtDecoderProvider.getIfAvailable() == null) {
                throw new ChaosDiagnosticException(missingJwtDecoderDiagnostic());
            }
        };
    }

    /**
     * 网关缺少 JWT 解码器时的诊断信息。
     */
    static ChaosDiagnostic missingJwtDecoderDiagnostic() {
        return new ChaosDiagnostic(
                "网关开启了 JWT 鉴权，但没有可用的 JWT 解码器",
                List.of(
                        "chaos.gateway.auth-enabled=true 且 chaos.gateway.token.type=JWT（默认值）",
                        "未配置 chaos.gateway.jwt.jwk-set-uri，也没有自定义 ReactiveJwtDecoder Bean，所有需要鉴权的请求都会返回 401"),
                List.of(
                        "配置 chaos.gateway.jwt.jwk-set-uri（授权服务器的 /oauth2/jwks 地址），并建议同时配置 issuer-uri 与 audiences",
                        "使用引用 token 时改为 chaos.gateway.token.type=OPAQUE 并配置 chaos.gateway.opaque-token.*",
                        "网关不负责验签（由下游资源服务验签）时设置 chaos.gateway.jwt.validation-enabled=false"));
    }

    /**
     * 注册默认 Gateway 限流 key 解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRateLimitKeyResolver gatewayRateLimitKeyResolver(ChaosGatewayProperties properties) {
        return new GatewayRateLimitKeyResolver(properties.getTrustedProxies());
    }

    /**
     * 注册 Gateway 全局限流过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRateLimitFilter gatewayRateLimitFilter(
            ChaosGatewayProperties properties,
            GatewayRateLimiter rateLimiter,
            GatewayRateLimitKeyResolver keyResolver,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new GatewayRateLimitFilter(properties, rateLimiter, keyResolver, metricsProvider.getIfAvailable());
    }

    /**
     * 注册灰度标签过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GrayTagFilter grayTagFilter(ChaosGatewayProperties properties) {
        return new GrayTagFilter(properties);
    }

    /**
     * 租户状态治理（chaos-tenant 为 chaos-gateway 的可选依赖）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.michael.chaos.tenant.TenantAccessValidator")
    static class TenantGatewayConfiguration {

        /**
         * 注册 Gateway 租户状态治理过滤器。
         */
        @Bean
        @ConditionalOnBean(TenantAccessValidator.class)
        @ConditionalOnMissingBean
        TenantGatewayFilter tenantGatewayFilter(
                ChaosGatewayProperties properties,
                TenantAccessValidator tenantAccessValidator,
                ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
            return new TenantGatewayFilter(
                    properties,
                    tenantAccessValidator,
                    auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new)
            );
        }
    }

    /**
     * 注册 Bearer token 鉴权过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationGatewayFilter jwtAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ObjectProvider<ReactiveJwtDecoder> jwtDecoderProvider,
            ObjectProvider<JwtRevocationService> jwtRevocationServiceProvider,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new JwtAuthenticationGatewayFilter(
                properties,
                jwtDecoderProvider.getIfAvailable(),
                jwtRevocationServiceProvider.getIfAvailable(),
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new),
                metricsProvider.getIfAvailable()
        );
    }

    /**
     * 注册网关授权决策服务。
     *
     * <p>网关只依赖 chaos-security-api，因此在这里独立组装 RBAC + 配置策略，不复用资源服务器的 Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.gateway.access", name = "enabled", havingValue = "true")
    public AuthorizationManager gatewayAuthorizationManager(
            ChaosGatewayProperties properties,
            ObjectProvider<AuthorizationPolicySource> policySourceProvider) {
        ChaosGatewayProperties.Access access = properties.getAccess();
        return AuthorizationManagerBuilder.create()
                .policyGroupId("chaos-gateway-access")
                .adminRoles(access.getAdminRoles())
                .roleHierarchy(access.getRoleHierarchy())
                .wildcardPermissionEnabled(access.isWildcardPermissionEnabled())
                .combiningAlgorithm(access.getCombiningAlgorithm())
                .policyDefinitions(access.toPolicyDefinitions(), "chaos.gateway.access.policies")
                .policySource(policySourceProvider.getIfAvailable())
                .build();
    }

    /**
     * 注册网关粗粒度鉴权过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AuthorizationManager.class)
    @ConditionalOnProperty(prefix = "chaos.gateway.access", name = "enabled", havingValue = "true")
    public AccessControlGatewayFilter accessControlGatewayFilter(
            ChaosGatewayProperties properties,
            AuthorizationManager authorizationManager,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new AccessControlGatewayFilter(
                properties,
                authorizationManager,
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new),
                metricsProvider.getIfAvailable()
        );
    }

    /**
     * 注册 opaque/reference token 鉴权过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    public OpaqueTokenAuthenticationGatewayFilter opaqueTokenAuthenticationGatewayFilter(
            ChaosGatewayProperties properties,
            ObjectProvider<ReactiveOpaqueTokenIntrospector> opaqueTokenIntrospectorProvider,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        return new OpaqueTokenAuthenticationGatewayFilter(
                properties,
                opaqueTokenIntrospectorProvider.getIfAvailable(),
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new)
        );
    }

    /**
     * 根据 JWK Set URI 注册响应式 JWT 解码器，并补齐 iss/aud/算法校验。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(NimbusReactiveJwtDecoder.class)
    @ConditionalOnProperty(prefix = "chaos.gateway.jwt", name = "jwk-set-uri")
    public ReactiveJwtDecoder reactiveJwtDecoder(ChaosGatewayProperties properties) {
        ChaosGatewayProperties.Jwt jwt = properties.getJwt();
        if (jwt.getIssuerUri() == null || jwt.getIssuerUri().isBlank() || jwt.getAudiences().isEmpty()) {
            LOGGER.warn(ChaosDiagnostic.of(
                    "网关 JWT 校验未限定签发方或受众",
                    "chaos.gateway.jwt.issuer-uri 或 chaos.gateway.jwt.audiences 未配置，同一 JWKS 为其他客户端签发的 token 也能通过网关",
                    "配置 chaos.gateway.jwt.issuer-uri（与授权服务器 chaos.authorization.issuer 一致）和 chaos.gateway.jwt.audiences"
            ).format());
        }
        return GatewayJwtDecoders.create(jwt);
    }

    /**
     * 根据 introspection URI 注册响应式 opaque token introspector。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(NimbusReactiveOpaqueTokenIntrospector.class)
    @ConditionalOnProperty(prefix = "chaos.gateway.opaque-token", name = "introspection-uri")
    public ReactiveOpaqueTokenIntrospector reactiveOpaqueTokenIntrospector(ChaosGatewayProperties properties) {
        ChaosGatewayProperties.OpaqueToken opaqueToken = properties.getOpaqueToken();
        if (opaqueToken.getClientId() == null || opaqueToken.getClientId().isBlank()
                || opaqueToken.getClientSecret() == null || opaqueToken.getClientSecret().isBlank()) {
            throw new ChaosDiagnosticException(ChaosDiagnostic.of(
                    "网关 opaque token introspection 缺少客户端凭据",
                    "已配置 chaos.gateway.opaque-token.introspection-uri，但 client-id 或 client-secret 为空，无法调用授权服务器校验 token",
                    "配置 chaos.gateway.opaque-token.client-id 与 chaos.gateway.opaque-token.client-secret"
                            + "（建议通过环境变量注入密钥，例如 client-secret: ${GATEWAY_INTROSPECTION_SECRET}）"));
        }
        NimbusReactiveOpaqueTokenIntrospector delegate = new NimbusReactiveOpaqueTokenIntrospector(
                opaqueToken.getIntrospectionUri(),
                opaqueToken.getClientId(),
                opaqueToken.getClientSecret()
        );
        return new CachingReactiveOpaqueTokenIntrospector(
                delegate,
                opaqueToken.getCacheTtl(),
                opaqueToken.getCacheMaxSize(),
                opaqueToken.getTimeout()
        );
    }

    /**
     * 注册 Gateway 访问日志过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.gateway", name = "request-timing-enabled", havingValue = "true", matchIfMissing = true)
    public GatewayAccessLogFilter gatewayAccessLogFilter(
            @org.springframework.beans.factory.annotation.Value("${spring.application.name:application}") String appName,
            @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:default}") String environment) {
        return new GatewayAccessLogFilter(appName, environment);
    }

    /**
     * 注册 Gateway 下游调用阶段耗时过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.gateway", name = "request-timing-enabled", havingValue = "true", matchIfMissing = true)
    public GatewayDownstreamTimingFilter gatewayDownstreamTimingFilter() {
        return new GatewayDownstreamTimingFilter();
    }

    /**
     * 注册 Gateway 统一降级异常处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayFallbackExceptionHandler gatewayFallbackExceptionHandler(ChaosGatewayProperties properties) {
        return new GatewayFallbackExceptionHandler(properties);
    }
}
