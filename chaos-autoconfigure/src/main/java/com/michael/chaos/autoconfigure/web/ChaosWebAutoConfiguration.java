package com.michael.chaos.autoconfigure.web;

import com.michael.chaos.autoconfigure.support.ProductionSafetyEnforcer;
import com.michael.chaos.autoconfigure.support.UnsafeForProduction;
import com.michael.chaos.core.idempotent.IdempotentRecordStore;
import com.michael.chaos.core.idempotent.IdempotentRepository;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRecordStore;
import com.michael.chaos.core.idempotent.support.InMemoryIdempotentRepository;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.ratelimit.RateLimiter;
import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import com.michael.chaos.web.advice.ResultResponseBodyAdvice;
import com.michael.chaos.web.config.ChaosWebMvcConfigurer;
import com.michael.chaos.web.config.ChaosWebProperties;
import com.michael.chaos.web.config.JacksonCustomizer;
import com.michael.chaos.web.exception.ErrorCodeHttpStatusMapper;
import com.michael.chaos.web.exception.GlobalExceptionHandler;
import com.michael.chaos.web.filter.AccessLogFilter;
import com.michael.chaos.web.filter.CurrentSpanProvider;
import com.michael.chaos.web.filter.TraceFilter;
import com.michael.chaos.web.i18n.ErrorMessageResolver;
import com.michael.chaos.web.i18n.MessageSourceErrorMessageResolver;
import com.michael.chaos.web.idempotent.DefaultIdempotentKeyGenerator;
import com.michael.chaos.web.idempotent.IdempotentInterceptor;
import com.michael.chaos.web.idempotent.IdempotentKeyGenerator;
import com.michael.chaos.web.idempotent.IdempotentResponseReplayFilter;
import com.michael.chaos.web.ratelimit.DefaultRateLimitKeyResolver;
import com.michael.chaos.web.ratelimit.RateLimitInterceptor;
import com.michael.chaos.web.ratelimit.RateLimitKeyResolver;
import com.michael.chaos.web.support.ClientIpResolver;
import com.michael.chaos.web.xss.XssFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Web starter 自动装配。
 *
 * <p>仅在 Servlet Web 应用中生效；WebFlux 网关等响应式应用不应注册 Servlet 过滤器和 MVC 拦截器。</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(value = DispatcherServlet.class, name = "com.michael.chaos.web.config.ChaosWebProperties")
@EnableConfigurationProperties(ChaosWebProperties.class)

public class ChaosWebAutoConfiguration {

    /**
     * Boot {@code ServerHttpObservationFilter} 的默认顺序为 {@code HIGHEST_PRECEDENCE + 1}；
     * 存在外部追踪时 TraceFilter 必须排在它之后才能读取到已创建的服务端 span。
     */
    private static final int TRACE_FILTER_ORDER_WITH_TRACING = Ordered.HIGHEST_PRECEDENCE + 2;

    /**
     * 幂等响应采集过滤器顺序。
     *
     * <p>必须排在 Spring Security 过滤器链（{@code SecurityProperties.DEFAULT_FILTER_ORDER = -100}）之后，
     * 否则未认证请求的 401 响应也会被包装；同时要排在 DispatcherServlet 之前才能包住整个 MVC 处理过程。</p>
     */
    private static final int IDEMPOTENT_REPLAY_FILTER_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * 注册全局异常处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(
            ObjectProvider<ErrorCodeHttpStatusMapper> mapperProvider,
            ObjectProvider<ErrorMessageResolver> messageResolverProvider) {
        return new GlobalExceptionHandler(mapperProvider.getIfAvailable(), messageResolverProvider.getIfAvailable());
    }

    /**
     * 注册统一响应包装增强。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.web", name = "response-wrap-enabled", havingValue = "true", matchIfMissing = true)
    public ResultResponseBodyAdvice resultResponseBodyAdvice() {
        return new ResultResponseBodyAdvice();
    }

    /**
     * 注册 Jackson 配置定制器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JacksonCustomizer jacksonCustomizer() {
        return new JacksonCustomizer();
    }

    /**
     * 注册客户端 IP 解析器，TraceFilter、限流等组件共享同一套可信代理规则。
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(ChaosWebProperties properties) {
        return new ClientIpResolver(properties.getForwarding().getTrustedProxies());
    }

    /**
     * 注册默认内存限流器。
     */
    @Bean
    @ConditionalOnMissingBean
    @UnsafeForProduction(value = InMemoryRateLimiter.class, message = "chaos-web: 生产环境不能使用 InMemoryRateLimiter，请引入 Redis/Redisson 限流实现")
    public RateLimiter rateLimiter(ChaosWebProperties properties) {
        return new InMemoryRateLimiter(properties.getRateLimit().getMaxLocalKeys());
    }

    /**
     * 注册默认限流 key 解析器。
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitKeyResolver rateLimitKeyResolver(ClientIpResolver clientIpResolver) {
        return new DefaultRateLimitKeyResolver(clientIpResolver);
    }

    /**
     * 注册限流拦截器。
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitInterceptor rateLimitInterceptor(
            ChaosWebProperties properties,
            RateLimiter rateLimiter,
            RateLimitKeyResolver keyResolver,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new RateLimitInterceptor(properties, rateLimiter, keyResolver, metricsProvider.getIfAvailable());
    }

    /**
     * 注册默认内存幂等存储。
     */
    @Bean
    @ConditionalOnMissingBean
    @UnsafeForProduction(value = InMemoryIdempotentRepository.class, message = "chaos-web: 生产环境不能使用 InMemoryIdempotentRepository，请引入 Redis/Redisson 幂等仓储")
    public IdempotentRepository idempotentRepository(ChaosWebProperties properties) {
        return new InMemoryIdempotentRepository(properties.getIdempotent().getMaxLocalKeys());
    }

    /**
     * 注册生产安全检查器。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosWebProductionSafetyChecker")
    public SmartInitializingSingleton chaosWebProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        return new ProductionSafetyEnforcer(environment, beanFactory);
    }

    /**
     * 注册默认幂等 key 生成器。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentKeyGenerator idempotentKeyGenerator() {
        return new DefaultIdempotentKeyGenerator();
    }

    /**
     * 注册内存响应快照存储。
     *
     * <p>只在开启响应回放时注册：关闭时注册一个永远不会被读写的 Bean，只会让生产安全检查多报一个无关告警。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.web.idempotent.replay", name = "enabled", havingValue = "true")
    @UnsafeForProduction(value = InMemoryIdempotentRecordStore.class,
            message = "chaos-web: 生产环境不能使用 InMemoryIdempotentRecordStore，"
                    + "快照只存在于单个实例上，重复请求打到其他实例会退化为 409；请引入 chaos-redis-starter")
    public IdempotentRecordStore idempotentRecordStore(ChaosWebProperties properties) {
        return new InMemoryIdempotentRecordStore(properties.getIdempotent().getReplay().getMaxLocalRecords());
    }

    /**
     * 注册幂等拦截器。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentInterceptor idempotentInterceptor(
            IdempotentRepository repository,
            IdempotentKeyGenerator keyGenerator,
            ChaosWebProperties properties,
            ObjectProvider<IdempotentRecordStore> recordStoreProvider,
            ObjectProvider<ErrorMessageResolver> messageResolverProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new IdempotentInterceptor(
                repository,
                keyGenerator,
                properties,
                recordStoreProvider.getIfAvailable(),
                messageResolverProvider.getIfAvailable(),
                metricsProvider.getIfAvailable());
    }

    /**
     * 注册幂等响应快照采集过滤器。
     *
     * <p>排在安全过滤器链之后（order 大于 {@code SecurityProperties.DEFAULT_FILTER_ORDER}），
     * 未通过认证的请求根本到不了这里，不会产生快照。</p>
     */
    @Bean
    @ConditionalOnBean(IdempotentRecordStore.class)
    @ConditionalOnProperty(prefix = "chaos.web.idempotent.replay", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<IdempotentResponseReplayFilter> idempotentResponseReplayFilter(
            IdempotentRecordStore recordStore,
            ChaosWebProperties properties,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        FilterRegistrationBean<IdempotentResponseReplayFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(
                new IdempotentResponseReplayFilter(recordStore, properties, metricsProvider.getIfAvailable()));
        registration.setOrder(IDEMPOTENT_REPLAY_FILTER_ORDER);
        return registration;
    }

    /**
     * 注册错误文案解析器。
     *
     * <p>{@code MessageSource} 用 {@code ObjectProvider} 惰性获取：它是 Spring 容器的基础设施 Bean，
     * 在自动装配阶段直接注入会把它的初始化时机提前到容器准备好之前。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorMessageResolver chaosErrorMessageResolver(
            ChaosWebProperties properties,
            ObjectProvider<MessageSource> messageSourceProvider) {
        if (!properties.getI18n().isEnabled()) {
            return ErrorMessageResolver.none();
        }
        String configured = properties.getI18n().getDefaultLocale();
        Locale fixedLocale = configured.isEmpty() ? null : Locale.forLanguageTag(configured.replace('_', '-'));
        return new MessageSourceErrorMessageResolver(messageSourceProvider::getIfAvailable, fixedLocale);
    }

    /**
     * 注册 Web MVC 扩展配置。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosWebMvcConfigurer chaosWebMvcConfigurer(
            RateLimitInterceptor rateLimitInterceptor,
            IdempotentInterceptor idempotentInterceptor,
            ChaosWebProperties properties) {
        return new ChaosWebMvcConfigurer(rateLimitInterceptor, idempotentInterceptor, properties);
    }

    /**
     * 注册 trace 过滤器。
     *
     * <p>没有外部追踪系统时排在最外层，保证整个过滤器链都能拿到 trace 上下文，并在 finally 中兜底清理 ThreadLocal；
     * 存在 Micrometer Tracing 时排在 Observation 过滤器之后，以复用其 span。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.web", name = "trace-enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TraceFilter> traceFilter(
            @Value("${spring.application.name:application}") String appName,
            ChaosWebProperties properties,
            ClientIpResolver clientIpResolver,
            ObjectProvider<CurrentSpanProvider> currentSpanProvider) {
        CurrentSpanProvider spanProvider = currentSpanProvider.getIfUnique();
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceFilter(
                appName,
                clientIpResolver,
                properties.getForwarding().isTrustIdentityHeaders(),
                spanProvider
        ));
        registration.setOrder(spanProvider == null ? Ordered.HIGHEST_PRECEDENCE : TRACE_FILTER_ORDER_WITH_TRACING);
        return registration;
    }

    /**
     * 注册访问日志过滤器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.web", name = "request-timing-enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter(
            @Value("${spring.application.name:application}") String appName,
            @Value("${spring.profiles.active:default}") String environment) {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccessLogFilter(appName, environment));
        registration.setOrder(Integer.MIN_VALUE + 10);
        return registration;
    }

    /**
     * 注册 XSS 防护过滤器。
     *
     * <p>默认关闭，需要显式配置 {@code chaos.web.xss-enabled=true}。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.web", name = "xss-enabled", havingValue = "true")
    public FilterRegistrationBean<XssFilter> xssFilter(ChaosWebProperties properties) {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter(properties.getXssExcludePaths()));
        registration.setOrder(Integer.MIN_VALUE + 20);
        return registration;
    }

    /**
     * Micrometer Tracing 集成。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class MicrometerTracingConfiguration {

        /**
         * 从 Micrometer Tracer 读取当前 span，使框架 traceId 与 APM 保持一致。
         */
        @Bean
        @ConditionalOnBean(Tracer.class)
        @ConditionalOnMissingBean
        CurrentSpanProvider micrometerCurrentSpanProvider(Tracer tracer) {
            return () -> {
                Span span = tracer.currentSpan();
                if (span == null || span.context() == null) {
                    return Optional.empty();
                }
                return Optional.of(new CurrentSpanProvider.SpanIds(span.context().traceId(), span.context().spanId()));
            };
        }
    }
}
