package com.michael.chaos.autoconfigure.security;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.autoconfigure.support.ProductionSafety;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.access.AccessExpressionEvaluator;
import com.michael.chaos.security.access.AccessSubjectFactory;
import com.michael.chaos.security.access.ClaimsPermissionResolver;
import com.michael.chaos.security.access.PermissionResolver;
import com.michael.chaos.security.access.RequireAccessAspect;
import com.michael.chaos.security.access.SubjectAttributeResolver;
import com.michael.chaos.security.access.env.RequestContextContributor;
import com.michael.chaos.security.access.env.ServletRequestContextContributor;
import com.michael.chaos.security.access.env.TimeContextContributor;
import com.michael.chaos.security.api.access.AccessPolicyFactory;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.CompositeAuthorizationContextContributor;
import com.michael.chaos.security.api.access.CompositeAuthorizationPolicy;
import com.michael.chaos.security.api.access.MapRoleHierarchy;
import com.michael.chaos.security.api.access.AuthorizationPolicy;
import com.michael.chaos.security.api.access.DefaultAuthorizationManager;
import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.access.RbacAuthorizationPolicy;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import com.michael.chaos.security.api.datascope.DataScopeAuthorizationService;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import com.michael.chaos.security.auth.SecurityContextLoginUserProvider;
import com.michael.chaos.security.auth.SecurityUtils;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import com.michael.chaos.security.context.SecurityContextRequestFilter;
import com.michael.chaos.security.oauth2.CachingOpaqueTokenIntrospector;
import com.michael.chaos.security.oauth2.LoginUserJwtAuthenticationConverter;
import com.michael.chaos.security.oauth2.LoginUserOpaqueTokenAuthenticationConverter;
import com.michael.chaos.security.permission.DataScopeAspect;
import com.michael.chaos.security.permission.DefaultPermissionCheckService;
import com.michael.chaos.security.permission.PermissionAspect;
import com.michael.chaos.security.permission.PermissionCheckService;
import com.michael.chaos.security.token.JwtRevocationFilter;
import com.michael.chaos.core.metrics.ChaosMetrics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

/**
 * Resource Server 安全自动装配。
 *
 * <p>显式声明先于 Spring Boot 默认安全自动装配执行：本类以 {@code @ConditionalOnMissingBean(SecurityFilterChain)}
 * 注册资源服务器过滤器链，如果晚于 Boot 默认配置，Boot 的表单登录过滤器链会先注册，导致本链被跳过。</p>
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
})
@ConditionalOnClass(value = HttpSecurity.class, name = "com.michael.chaos.security.config.ChaosSecurityProperties")
@ConditionalOnProperty(prefix = "chaos.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChaosSecurityProperties.class)

public class ChaosSecurityAutoConfiguration {

    /**
     * 访问主体工厂，由本类的 {@link #chaosAccessSubjectFactory} 注册，业务侧可以覆盖。
     *
     * <p>用字段注入而不是给 {@code permissionAuthorizationService} 加参数：已发布的 {@code @Bean} 方法签名
     * 属于公开 API，MINOR 版本改签名会被 japicmp 兼容门禁拦下。</p>
     */
    @Autowired
    private ObjectProvider<AccessSubjectFactory> accessSubjectFactoryProvider;

    /**
     * 注册 JWT 到 LoginUser 的转换器。
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginUserJwtAuthenticationConverter loginUserJwtAuthenticationConverter() {
        return new LoginUserJwtAuthenticationConverter();
    }

    /**
     * 注册 opaque token 到 LoginUser 的转换器。
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginUserOpaqueTokenAuthenticationConverter loginUserOpaqueTokenAuthenticationConverter() {
        return new LoginUserOpaqueTokenAuthenticationConverter();
    }

    /**
     * 注册资源服务器安全过滤器链。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ChaosSecurityProperties properties,
            LoginUserJwtAuthenticationConverter converter,
            LoginUserOpaqueTokenAuthenticationConverter opaqueTokenConverter,
            ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospectorProvider,
            JwtRevocationFilter jwtRevocationFilter,
            SecurityContextRequestFilter securityContextRequestFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(properties.getPermitAll()).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> {
                    if (properties.getToken().getType() == ChaosSecurityProperties.TokenType.OPAQUE) {
                        resourceServer.opaqueToken(opaque -> {
                            opaque.authenticationConverter(opaqueTokenConverter);
                            OpaqueTokenIntrospector introspector = opaqueTokenIntrospectorProvider.getIfAvailable();
                            if (introspector != null) {
                                opaque.introspector(introspector);
                            }
                        });
                        return;
                    }
                    resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(converter));
                })
                .addFilterAfter(jwtRevocationFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(securityContextRequestFilter, JwtRevocationFilter.class);
        if (properties.isHttpBasicEnabled()) {
            http.httpBasic(Customizer.withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }

    /**
     * 根据 chaos 配置注册 opaque token introspection 客户端。
     *
     * <p>默认的 {@code NimbusOpaqueTokenIntrospector(uri, clientId, secret)} 使用无超时的 RestTemplate，
     * 且每个请求都远程调用授权服务器。这里显式设置连接/读取超时，并包一层短期成功结果缓存，
     * 避免授权服务器抖动拖垮资源服务线程池。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.security.opaque-token", name = "introspection-uri")
    public OpaqueTokenIntrospector opaqueTokenIntrospector(ChaosSecurityProperties properties) {
        ChaosSecurityProperties.OpaqueToken opaqueToken = properties.getOpaqueToken();
        if (opaqueToken.getClientId() == null || opaqueToken.getClientId().isBlank()
                || opaqueToken.getClientSecret() == null || opaqueToken.getClientSecret().isBlank()) {
            throw new ChaosDiagnosticException(ChaosDiagnostic.of(
                    "资源服务 opaque token introspection 缺少客户端凭据",
                    "已配置 chaos.security.opaque-token.introspection-uri，但 client-id 或 client-secret 为空，无法调用授权服务器校验 token",
                    "配置 chaos.security.opaque-token.client-id 与 chaos.security.opaque-token.client-secret"
                            + "（建议通过环境变量注入密钥）；使用 JWT 时改为 chaos.security.token.type=JWT 并删除 opaque-token 配置"));
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (opaqueToken.getConnectTimeout() != null) {
            requestFactory.setConnectTimeout(opaqueToken.getConnectTimeout());
        }
        if (opaqueToken.getReadTimeout() != null) {
            requestFactory.setReadTimeout(opaqueToken.getReadTimeout());
        }
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(
                new BasicAuthenticationInterceptor(opaqueToken.getClientId(), opaqueToken.getClientSecret()));
        OpaqueTokenIntrospector remote = new NimbusOpaqueTokenIntrospector(
                opaqueToken.getIntrospectionUri(),
                restTemplate
        );
        return new CachingOpaqueTokenIntrospector(remote, opaqueToken.getCacheTtl(), opaqueToken.getCacheMaxSize());
    }

    /**
     * 注册用户和租户上下文同步过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityContextRequestFilter securityContextRequestFilter() {
        return new SecurityContextRequestFilter();
    }

    /**
     * 注册基于 Spring Security 上下文的登录用户提供者。
     *
     * <p>chaos-mybatis 等只依赖 chaos-security-api 的模块通过该 SPI 获取当前用户，而不是直接调用 {@code SecurityUtils}。</p>
     */
    @Bean
    @ConditionalOnMissingBean(LoginUserProvider.class)
    public LoginUserProvider loginUserProvider() {
        return new SecurityContextLoginUserProvider();
    }

    /**
     * 注册默认空 JWT 撤销服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtRevocationService jwtRevocationService() {
        return new NoopJwtRevocationService();
    }

    /**
     * 生产环境禁止使用空 JWT 撤销服务。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosSecurityProductionSafetyChecker")
    public SmartInitializingSingleton chaosSecurityProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        return () -> ProductionSafety.warnUnsafeDefaultBeans(
                environment,
                beanFactory,
                Map.of(
                        NoopJwtRevocationService.class.getName(),
                        "chaos-security: 生产环境不能使用 NoopJwtRevocationService，请引入 Redis JWT 黑名单实现或关闭 JWT 撤销检查"
                )
        );
    }

    /**
     * 注册 JWT 撤销检查过滤器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public JwtRevocationFilter jwtRevocationFilter(
            JwtRevocationService revocationService,
            ChaosSecurityProperties properties) {
        return new JwtRevocationFilter(revocationService, properties);
    }

    /**
     * 注册默认 RBAC 授权策略。
     */
    @Bean
    @ConditionalOnMissingBean(RbacAuthorizationPolicy.class)
    public RbacAuthorizationPolicy rbacAuthorizationPolicy(ChaosSecurityProperties properties) {
        ChaosSecurityProperties.Access access = properties.getAccess();
        return new RbacAuthorizationPolicy(
                "rbac",
                Set.copyOf(access.getAdminRoles()),
                new MapRoleHierarchy(access.getRoleHierarchy()),
                access.isWildcardPermissionEnabled());
    }

    /**
     * 把 {@code chaos.security.access.policies} 配置的 ABAC 策略注册为一条组合策略。
     *
     * <p>组内按 {@code chaos.security.access.combining-algorithm} 合并；组合策略与 RBAC 之间恒为拒绝优先，
     * 保证配置里的 DENY 规则一定能否决 RBAC 的放行。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosConfiguredAccessPolicy")
    public CompositeAuthorizationPolicy chaosConfiguredAccessPolicy(ChaosSecurityProperties properties) {
        ChaosSecurityProperties.Access access = properties.getAccess();
        return new CompositeAuthorizationPolicy(
                "chaos-configured-access",
                AccessPolicyFactory.create(access.toPolicyDefinitions(), "chaos.security.access.policies"),
                access.getCombiningAlgorithm());
    }

    /**
     * 注册默认权限解析器：直接使用令牌里的权限。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionResolver permissionResolver() {
        return new ClaimsPermissionResolver();
    }

    /**
     * 注册访问主体工厂。
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessSubjectFactory chaosAccessSubjectFactory(
            PermissionResolver permissionResolver,
            ObjectProvider<SubjectAttributeResolver> subjectAttributeResolvers) {
        return new AccessSubjectFactory(permissionResolver, subjectAttributeResolvers.orderedStream().toList());
    }

    /**
     * 注册 {@code @RequireAccess} 表达式求值器。
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessExpressionEvaluator accessExpressionEvaluator() {
        return new AccessExpressionEvaluator();
    }

    /**
     * 注册时间环境属性贡献者。
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeContextContributor timeContextContributor() {
        return new TimeContextContributor();
    }

    /**
     * 注册请求上下文环境属性贡献者。
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestContextContributor requestContextContributor() {
        return new RequestContextContributor();
    }

    /**
     * 把所有环境属性贡献者合并成一个，使用方只依赖单个贡献者。
     */
    @Bean
    @ConditionalOnMissingBean(CompositeAuthorizationContextContributor.class)
    public CompositeAuthorizationContextContributor chaosAuthorizationContextContributor(
            ObjectProvider<AuthorizationContextContributor> contextContributors) {
        return new CompositeAuthorizationContextContributor(contextContributors.orderedStream()
                .filter(contributor -> !(contributor instanceof CompositeAuthorizationContextContributor))
                .toList());
    }

    /**
     * 注册 RequireAccess 统一授权切面。
     */
    @Bean
    @ConditionalOnMissingBean
    public RequireAccessAspect requireAccessAspect(
            AuthorizationManager authorizationManager,
            AccessSubjectFactory accessSubjectFactory,
            AccessExpressionEvaluator accessExpressionEvaluator,
            CompositeAuthorizationContextContributor authorizationContextContributor,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new RequireAccessAspect(
                authorizationManager,
                accessSubjectFactory,
                accessExpressionEvaluator,
                authorizationContextContributor,
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new),
                metricsProvider.getIfAvailable());
    }

    /**
     * 注册默认授权决策服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationManager authorizationManager(List<AuthorizationPolicy> policies) {
        return new DefaultAuthorizationManager(policies);
    }

    /**
     * 注册权限编码授权服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionAuthorizationService permissionAuthorizationService(AuthorizationManager authorizationManager) {
        return new PermissionAuthorizationService(
                authorizationManager,
                accessSubjectFactoryProvider.getIfAvailable(() -> new AccessSubjectFactory(null)));
    }

    /**
     * 把统一权限授权服务注册到 {@link SecurityUtils}，使静态 hasPermission 与 {@code @Permission} 判定一致。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosSecurityUtilsInitializer")
    public SmartInitializingSingleton chaosSecurityUtilsInitializer(
            ObjectProvider<PermissionAuthorizationService> permissionAuthorizationServiceProvider) {
        return () -> SecurityUtils.setPermissionAuthorizationService(
                permissionAuthorizationServiceProvider.getIfUnique());
    }

    /**
     * 注册数据权限授权服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataScopeAuthorizationService dataScopeAuthorizationService(AuthorizationManager authorizationManager) {
        return new DataScopeAuthorizationService(authorizationManager);
    }

    /**
     * 注册默认权限校验服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionCheckService permissionCheckService(PermissionAuthorizationService authorizationService) {
        return new DefaultPermissionCheckService(authorizationService);
    }

    /**
     * 注册 Permission 方法权限切面。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionAspect permissionAspect(
            PermissionCheckService permissionCheckService,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider,
            ObjectProvider<ChaosMetrics> metricsProvider) {
        return new PermissionAspect(
                permissionCheckService,
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new),
                metricsProvider.getIfAvailable()
        );
    }

    /**
     * 注册 DataScope 数据权限上下文切面。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataScopeAspect dataScopeAspect() {
        return new DataScopeAspect();
    }

    /**
     * Servlet 环境下的授权环境属性。
     *
     * <p>单独放在嵌套配置里：{@code @Bean} 方法签名引用了 servlet 相关类型，必须先确认这些类存在。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.context.request.RequestContextHolder"
    })
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class ServletAccessContextConfiguration {

        /**
         * 注册 Servlet 请求环境属性贡献者。
         */
        @Bean
        @ConditionalOnMissingBean
        public ServletRequestContextContributor servletRequestContextContributor() {
            return new ServletRequestContextContributor();
        }
    }
}
