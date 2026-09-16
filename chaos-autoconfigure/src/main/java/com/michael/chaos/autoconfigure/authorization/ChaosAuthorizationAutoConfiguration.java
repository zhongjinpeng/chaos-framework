package com.michael.chaos.autoconfigure.authorization;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.captcha.AwtCaptchaImageGenerator;
import com.michael.chaos.authorization.captcha.CaptchaController;
import com.michael.chaos.authorization.captcha.CaptchaImageGenerator;
import com.michael.chaos.authorization.captcha.CaptchaService;
import com.michael.chaos.authorization.captcha.CaptchaStore;
import com.michael.chaos.authorization.captcha.DefaultCaptchaService;
import com.michael.chaos.authorization.core.AuthorizationProductionSafetyChecker;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.core.ChaosAuthorizationUserService;
import com.michael.chaos.authorization.core.RegisteredClientIds;
import com.michael.chaos.authorization.core.RejectingChaosAuthorizationUserService;
import com.michael.chaos.authorization.grant.ChaosAuthorizationGrantTypes;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationConverter;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationHandler;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationProvider;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.AuthorizationTokenRevoker;
import com.michael.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.IndexedAuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.NoopAuthorizationKickoutService;
import com.michael.chaos.authorization.logout.ChaosLogoutController;
import com.michael.chaos.authorization.password.InMemoryLoginFailureLimiter;
import com.michael.chaos.authorization.password.LoginFailureLimiter;
import com.michael.chaos.authorization.password.PasswordGrantAuthenticationHandler;
import com.michael.chaos.authorization.sms.RejectingSmsCodeVerifier;
import com.michael.chaos.authorization.sms.SmsCodeGrantAuthenticationHandler;
import com.michael.chaos.authorization.sms.SmsCodeVerifier;
import com.michael.chaos.authorization.token.ChaosRefreshTokenAuthenticationProvider;
import com.michael.chaos.authorization.token.ChaosTokenCustomizer;
import com.michael.chaos.authorization.token.ChaosTokenRevocationAuthenticationProvider;
import com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorization starter 自动装配。
 *
 * <p>显式声明先于资源服务器安全自动装配和 Spring Boot 默认安全/授权服务器自动装配执行，
 * 保证授权服务器端点过滤器链（{@code @Order(1)}）稳定优先注册，而不是依赖自动装配类名的字母顺序。</p>
 */
@AutoConfiguration(beforeName = {
        "com.michael.chaos.autoconfigure.security.ChaosSecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerAutoConfiguration"
})
@ConditionalOnClass(value = OAuth2AuthorizationServerConfigurer.class, name = "com.michael.chaos.authorization.core.ChaosAuthorizationProperties")
@ConditionalOnProperty(prefix = "chaos.authorization", name = "enabled", havingValue = "true", matchIfMissing = true)
// 授权服务器过滤器链依赖 Servlet 栈的 HttpSecurity；非 Web 或响应式应用中不应装配。
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ChaosAuthorizationProperties.class)
public class ChaosAuthorizationAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaosAuthorizationAutoConfiguration.class);

    /**
     * Redis / JDBC 存储都是可选依赖：只在类路径存在时才调用对应的创建入口（见 {@link AuthorizationRedisStores}）。
     */
    private static final boolean REDIS_PRESENT =
            AuthorizationRedisStores.isPresent(ChaosAuthorizationAutoConfiguration.class.getClassLoader());

    private static final boolean JDBC_PRESENT =
            AuthorizationJdbcStores.isPresent(ChaosAuthorizationAutoConfiguration.class.getClassLoader());

    private static void requireRedis(String mode) {
        if (!REDIS_PRESENT) {
            throw new IllegalStateException(mode + " requires spring-data-redis and chaos-security-redis on the classpath");
        }
    }

    private static void requireJdbc(String mode) {
        if (!JDBC_PRESENT) {
            throw new IllegalStateException(mode + " requires spring-jdbc on the classpath");
        }
    }

    /**
     * 注册授权服务器安全过滤器链，并接入自定义 grant_type 处理流程。
     */
    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "authorizationServerSecurityFilterChain")
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            List<ChaosGrantAuthenticationHandler> handlers,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<?> tokenGenerator,
            AuthorizationKickoutService kickoutService,
            AuthorizationSessionRegistry sessionRegistry,
            JwtRevocationService jwtRevocationService,
            ChaosAuthorizationProperties properties,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) throws Exception {
        AuditEventPublisher auditEventPublisher =
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new);
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                .accessTokenRequestConverter(new ChaosGrantAuthenticationConverter(handlers))
                                .authenticationProvider(new ChaosGrantAuthenticationProvider(
                                        handlers,
                                        authorizationService,
                                        tokenGenerator,
                                        kickoutService,
                                        auditEventPublisher
                                ))
                                .authenticationProvider(new ChaosRefreshTokenAuthenticationProvider(
                                        authorizationService,
                                        tokenGenerator,
                                        sessionRegistry,
                                        auditEventPublisher,
                                        properties
                                ))
                        )
                        .tokenRevocationEndpoint(revocationEndpoint -> revocationEndpoint
                                .authenticationProvider(new ChaosTokenRevocationAuthenticationProvider(
                                        authorizationService,
                                        sessionRegistry,
                                        jwtRevocationService,
                                        auditEventPublisher
                                ))
                        )
                        .oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    /**
     * 注册默认用户服务，业务系统未提供实现时拒绝所有登录。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosAuthorizationUserService chaosAuthorizationUserService() {
        return new RejectingChaosAuthorizationUserService();
    }

    /**
     * 注册框架统一退出登录端点。
     *
     * <p>业务系统无需各自实现 logout;可通过 {@code chaos.authorization.logout.enabled=false}
     * 关闭，或 {@code chaos.authorization.logout.path} 调整路径。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.logout", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChaosLogoutController chaosLogoutController(
            OAuth2AuthorizationService authorizationService,
            AuthorizationSessionRegistry sessionRegistry,
            JwtRevocationService jwtRevocationService,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        return new ChaosLogoutController(
                authorizationService,
                sessionRegistry,
                new AuthorizationTokenRevoker(jwtRevocationService),
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new)
        );
    }

    /**
     * 注册生产安全检查器，提示授权服务器仍在使用开发默认值。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationProductionSafetyChecker authorizationProductionSafetyChecker(
            ChaosAuthorizationProperties properties,
            Environment environment,
            ObjectProvider<RegisteredClientRepository> registeredClientRepositoryProvider,
            ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider,
            ObjectProvider<OAuth2AuthorizationConsentService> authorizationConsentServiceProvider,
            ObjectProvider<AuthorizationSessionRegistry> sessionRegistryProvider,
            ObjectProvider<AuthorizationKickoutService> kickoutServiceProvider,
            ObjectProvider<JwtRevocationService> jwtRevocationServiceProvider,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        return new AuthorizationProductionSafetyChecker(
                properties,
                environment,
                registeredClientRepositoryProvider.getIfAvailable(),
                authorizationServiceProvider.getIfAvailable(),
                authorizationConsentServiceProvider.getIfAvailable(),
                sessionRegistryProvider.getIfAvailable(),
                kickoutServiceProvider.getIfAvailable(),
                jwtRevocationServiceProvider.getIfAvailable(),
                auditEventPublisherProvider.getIfAvailable()
        );
    }

    /**
     * 注册默认短信验证码校验器，业务系统未提供实现时拒绝所有验证码。
     */
    @Bean
    @ConditionalOnMissingBean
    public SmsCodeVerifier smsCodeVerifier() {
        return new RejectingSmsCodeVerifier();
    }

    /**
     * 注册默认用户名密码登录处理器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.authorization.grant", name = "default-password-enabled", havingValue = "true", matchIfMissing = true)
    public PasswordGrantAuthenticationHandler passwordGrantAuthenticationHandler(
            ChaosAuthorizationUserService userService,
            ObjectProvider<CaptchaService> captchaServiceProvider,
            ObjectProvider<LoginFailureLimiter> loginFailureLimiterProvider) {
        return new PasswordGrantAuthenticationHandler(
                userService,
                captchaServiceProvider.getIfAvailable(),
                loginFailureLimiterProvider.getIfAvailable()
        );
    }

    /**
     * 注册登录失败锁定限制器：存在 StringRedisTemplate 时使用 Redis 共享计数，否则使用单实例内存计数。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.login-lock", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoginFailureLimiter loginFailureLimiter(ChaosAuthorizationProperties properties, BeanFactory beanFactory) {
        ChaosAuthorizationProperties.LoginLock loginLock = properties.getLoginLock();
        if (REDIS_PRESENT && AuthorizationRedisStores.hasStringRedisTemplate(beanFactory)) {
            return AuthorizationRedisStores.loginFailureLimiter(beanFactory, loginLock);
        }
        LOGGER.info("未检测到 StringRedisTemplate，登录失败锁定使用本地内存计数，多实例部署时各实例独立计数");
        return new InMemoryLoginFailureLimiter(
                loginLock.getMaxFailures(),
                loginLock.getLockDuration(),
                loginLock.getMaxLocalEntries()
        );
    }

    /**
     * 注册 JDK AWT 图形验证码生成器。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.captcha", name = "enabled", havingValue = "true")
    public CaptchaImageGenerator captchaImageGenerator(ChaosAuthorizationProperties properties) {
        ChaosAuthorizationProperties.Captcha captcha = properties.getCaptcha();
        return new AwtCaptchaImageGenerator(captcha.getLength(), captcha.getWidth(), captcha.getHeight());
    }

    /**
     * 注册 Redis 一次性验证码存储。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.captcha", name = "enabled", havingValue = "true")
    public CaptchaStore captchaStore(ChaosAuthorizationProperties properties, BeanFactory beanFactory) {
        if (!REDIS_PRESENT) {
            throw new IllegalStateException("Captcha requires spring-data-redis and chaos-security-redis on the classpath");
        }
        return AuthorizationRedisStores.captchaStore(beanFactory, properties);
    }

    /**
     * 注册默认图形验证码服务。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.captcha", name = "enabled", havingValue = "true")
    public CaptchaService captchaService(
            CaptchaImageGenerator imageGenerator,
            CaptchaStore captchaStore,
            ChaosAuthorizationProperties properties) {
        return new DefaultCaptchaService(imageGenerator, captchaStore, properties.getCaptcha().getTtl());
    }

    /**
     * 注册图形验证码公开端点。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.captcha", name = "enabled", havingValue = "true")
    public CaptchaController captchaController(CaptchaService captchaService) {
        return new CaptchaController(captchaService);
    }

    /**
     * 注册默认手机号验证码登录处理器。
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.authorization.grant", name = "default-sms-enabled", havingValue = "true", matchIfMissing = true)
    public SmsCodeGrantAuthenticationHandler smsCodeGrantAuthenticationHandler(
            ChaosAuthorizationUserService userService,
            SmsCodeVerifier smsCodeVerifier) {
        return new SmsCodeGrantAuthenticationHandler(userService, smsCodeVerifier);
    }

    /**
     * 注册内存 OAuth2 客户端仓储。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.client", name = "store-type", havingValue = "memory", matchIfMissing = true)
    public RegisteredClientRepository inMemoryRegisteredClientRepository(
            ChaosAuthorizationProperties properties,
            ObjectProvider<ChaosGrantAuthenticationHandler> grantHandlers) {
        return new InMemoryRegisteredClientRepository(
                defaultRegisteredClient(properties, grantHandlers));
    }

    /**
     * 注册 Redis OAuth2 客户端仓储，并把配置里的默认客户端写入 Redis。
     *
     * <p>写入是幂等的：主键由 clientId 确定性推导（{@link RegisteredClientIds}），
     * 每次启动落在同一条记录上，改了密钥或作用域也会原地覆盖，不会堆出多份客户端。</p>
     *
     * <p>token.type=redis 时应当配套使用本仓储：授权记录持久化而客户端注册信息留在内存里，
     * 会让每次重启都作废全部已签发令牌。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.client", name = "store-type", havingValue = "redis")
    public RegisteredClientRepository redisRegisteredClientRepository(
            ChaosAuthorizationProperties properties,
            BeanFactory beanFactory,
            ObjectProvider<ChaosGrantAuthenticationHandler> grantHandlers) {
        requireRedis("Redis client store mode");
        return AuthorizationRedisStores.registeredClientRepository(
                beanFactory, properties, defaultRegisteredClient(properties, grantHandlers));
    }

    /**
     * 注册 JDBC OAuth2 客户端仓储。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "chaos.authorization.client", name = "store-type", havingValue = "jdbc")
    public RegisteredClientRepository jdbcRegisteredClientRepository(BeanFactory beanFactory) {
        requireJdbc("JDBC client store mode");
        return AuthorizationJdbcStores.registeredClientRepository(beanFactory);
    }

    /**
     * 按配置构建默认 OAuth2 客户端。
     *
     * <p>主键**必须**是确定性的，不能用 {@code UUID.randomUUID()}：授权记录里存着签发时的
     * registeredClientId，主键每次启动都变的话，重启后 introspect / refresh_token 都会
     * 反查不到客户端，把还没过期的令牌判成 inactive —— 表现就是「重启一次，全员重新登录」。</p>
     */
    private RegisteredClient defaultRegisteredClient(
            ChaosAuthorizationProperties properties,
            ObjectProvider<ChaosGrantAuthenticationHandler> grantHandlers) {
        OAuth2TokenFormat accessTokenFormat = properties.getToken().getType() == ChaosAuthorizationProperties.TokenType.REDIS
                ? OAuth2TokenFormat.REFERENCE
                : OAuth2TokenFormat.SELF_CONTAINED;
        return RegisteredClient.withId(RegisteredClientIds.stableId(properties.getClient().getId()))
                .clientId(properties.getClient().getId())
                .clientSecret(properties.getClient().getSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(ChaosAuthorizationGrantTypes.PASSWORD)
                .authorizationGrantType(ChaosAuthorizationGrantTypes.SMS_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // 应用注册的自定义 grant 处理器也要落到客户端的授权类型上，否则 token 端点会以
                // unauthorized_client 拒掉 —— ChaosGrantAuthenticationHandler 这个 SPI
                // 本来就是给应用扩展登录方式用的，客户端不认它等于 SPI 形同虚设。
                .authorizationGrantTypes(types -> grantHandlers.orderedStream()
                        .map(ChaosGrantAuthenticationHandler::grantType)
                        .forEach(types::add))
                .scopes(scopes -> scopes.addAll(List.of(properties.getClient().getScopes())))
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(properties.getAccessTokenTtl())
                        .accessTokenFormat(accessTokenFormat)
                        .refreshTokenTimeToLive(properties.getRefreshTokenTtl())
                        .reuseRefreshTokens(properties.isReuseRefreshTokens())
                        .build())
                .build();
    }

    /**
     * 根据 token 模式注册授权存储服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService authorizationService(
            RegisteredClientRepository registeredClientRepository,
            ChaosAuthorizationProperties properties,
            BeanFactory beanFactory) {
        if (properties.getToken().getType() == ChaosAuthorizationProperties.TokenType.REDIS) {
            requireRedis("Redis token mode");
            return AuthorizationRedisStores.authorizationService(beanFactory, properties);
        }
        if (properties.getClient().getStoreType() == ChaosAuthorizationProperties.ClientStoreType.JDBC) {
            requireJdbc("JDBC authorization mode");
            return AuthorizationJdbcStores.authorizationService(beanFactory, registeredClientRepository);
        }
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService();
    }

    /**
     * 注册授权同意服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            RegisteredClientRepository registeredClientRepository,
            ChaosAuthorizationProperties properties,
            BeanFactory beanFactory) {
        ChaosAuthorizationProperties.ConsentStoreType storeType = properties.getConsent().getStoreType();
        if (storeType == ChaosAuthorizationProperties.ConsentStoreType.AUTO) {
            storeType = properties.getToken().getType() == ChaosAuthorizationProperties.TokenType.REDIS
                    ? ChaosAuthorizationProperties.ConsentStoreType.REDIS
                    : properties.getClient().getStoreType() == ChaosAuthorizationProperties.ClientStoreType.JDBC
                            ? ChaosAuthorizationProperties.ConsentStoreType.JDBC
                            : ChaosAuthorizationProperties.ConsentStoreType.MEMORY;
        }
        if (storeType == ChaosAuthorizationProperties.ConsentStoreType.JDBC) {
            requireJdbc("JDBC authorization consent mode");
            return AuthorizationJdbcStores.authorizationConsentService(beanFactory, registeredClientRepository);
        }
        if (storeType == ChaosAuthorizationProperties.ConsentStoreType.REDIS) {
            requireRedis("Redis authorization consent mode");
            return AuthorizationRedisStores.authorizationConsentService(beanFactory, properties);
        }
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService();
    }

    /**
     * 注册 JWT 撤销服务。
     *
     * <p>与资源服务器、网关共用 chaos-security-redis 中唯一的 {@link RedisJwtRevocationService}：
     * 引入 chaos-redis-starter 时由 {@code ChaosSecurityRedisAutoConfiguration} 先行注册；授权服务器只引入
     * Spring Data Redis 时在这里用 {@link StringRedisTemplate} 注册同一实现。必须使用字符串序列化的模板，
     * 旧实现使用 {@code RedisTemplate<Object, Object>}（JDK 序列化 key），写入的黑名单对资源服务器不可见。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtRevocationService authorizationJwtRevocationService(BeanFactory beanFactory) {
        if (REDIS_PRESENT && AuthorizationRedisStores.hasStringRedisTemplate(beanFactory)) {
            return AuthorizationRedisStores.jwtRevocationService(beanFactory);
        }
        return new NoopJwtRevocationService();
    }

    /**
     * 注册授权会话索引。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationSessionRegistry authorizationSessionRegistry(
            ChaosAuthorizationProperties properties,
            BeanFactory beanFactory) {
        ChaosAuthorizationProperties.SessionRegistryType registryType =
                properties.getKickout().getSessionRegistryType();
        if (registryType == ChaosAuthorizationProperties.SessionRegistryType.REDIS) {
            requireRedis("Redis authorization session registry");
            return AuthorizationRedisStores.sessionRegistry(beanFactory, properties);
        }
        if (registryType == ChaosAuthorizationProperties.SessionRegistryType.AUTO
                && REDIS_PRESENT && AuthorizationRedisStores.hasObjectRedisTemplate(beanFactory)) {
            return AuthorizationRedisStores.sessionRegistry(beanFactory, properties);
        }
        return new InMemoryAuthorizationSessionRegistry(properties.getKickout().getMaxLocalSessions());
    }

    /**
     * 注册互踢服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationKickoutService authorizationKickoutService(
            OAuth2AuthorizationService authorizationService,
            AuthorizationSessionRegistry sessionRegistry,
            JwtRevocationService jwtRevocationService,
            ChaosAuthorizationProperties properties,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        AuditEventPublisher auditEventPublisher =
                auditEventPublisherProvider.getIfAvailable(NoopAuditEventPublisher::new);
        if (!properties.getKickout().isEnabled()) {
            return new NoopAuthorizationKickoutService();
        }
        if (REDIS_PRESENT) {
            AuthorizationKickoutService redisKickoutService = AuthorizationRedisStores.kickoutService(
                    authorizationService, properties, jwtRevocationService, auditEventPublisher);
            if (redisKickoutService != null) {
                return redisKickoutService;
            }
        }
        return new IndexedAuthorizationKickoutService(
                authorizationService,
                sessionRegistry,
                jwtRevocationService,
                properties,
                auditEventPublisher
        );
    }

    /**
     * 注册授权服务器基础设置。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationServerSettings authorizationServerSettings(ChaosAuthorizationProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.getIssuer()).build();
    }

    /**
     * 注册 JWT claim 定制器。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosTokenCustomizer chaosTokenCustomizer() {
        return new ChaosTokenCustomizer();
    }

    /**
     * 注册 OAuth2 token 生成器。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenGenerator<?> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            ChaosTokenCustomizer tokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(
                new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(signingKeySource(jwkSource)));
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        accessTokenGenerator.setAccessTokenCustomizer(tokenCustomizer::customizeOpaque);
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                accessTokenGenerator,
                refreshTokenGenerator
        );
    }

    /**
     * 返回只包含私钥的签名密钥源。
     *
     * <p>JWKS 中可能包含轮换保留的旧公钥，签发时只允许使用带私钥的当前密钥，
     * 否则 Nimbus 编码器会因匹配到多把密钥而拒绝签名。</p>
     */
    static JWKSource<SecurityContext> signingKeySource(JWKSource<SecurityContext> jwkSource) {
        return (selector, context) -> jwkSource.get(selector, context)
                .stream()
                .filter(JWK::isPrivate)
                .toList();
    }

    /**
     * 注册 JWT 解码器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * 注册默认 JWK 源。
     */
    @Bean
    @ConditionalOnMissingBean
    public JWKSource<SecurityContext> jwkSource(ChaosAuthorizationProperties properties) {
        List<JWK> keys = new ArrayList<>();
        keys.add(loadOrGenerateRsa(properties.getJwk()));
        // 旧公钥只参与 JWKS 发布和验签，保证密钥轮换期间在途 token 仍可被资源服务器校验。
        for (ChaosAuthorizationProperties.PreviousKey previousKey : properties.getJwk().getPreviousPublicKeys()) {
            keys.add(loadPublicRsa(previousKey));
        }
        return new ImmutableJWKSet<>(new JWKSet(keys));
    }

    /**
     * 优先加载配置的 RSA 密钥，未配置时生成临时 RSA JWK。
     */
    private RSAKey loadOrGenerateRsa(ChaosAuthorizationProperties.Jwk properties) {
        if (properties.getPublicKeyLocation() != null && properties.getPrivateKeyLocation() != null) {
            return loadRsa(properties);
        }
        return generateRsa(properties.getKeyId());
    }

    /**
     * 从 PEM 文件加载 RSA JWK。
     */
    private RSAKey loadRsa(ChaosAuthorizationProperties.Jwk properties) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(
                    readPem(properties.getPublicKeyLocation())));
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                    readPem(properties.getPrivateKeyLocation())));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(properties.getKeyId())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA JWK", ex);
        }
    }

    /**
     * 从 PEM 文件加载仅含公钥的旧 RSA JWK。
     */
    private RSAKey loadPublicRsa(ChaosAuthorizationProperties.PreviousKey previousKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(
                    readPem(previousKey.getPublicKeyLocation())));
            return new RSAKey.Builder(publicKey)
                    .keyID(previousKey.getKeyId())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load previous RSA public key: " + previousKey.getKeyId(), ex);
        }
    }

    /**
     * 生成临时 RSA JWK。
     */
    private RSAKey generateRsa(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(keyId == null || keyId.isBlank() ? UUID.randomUUID().toString() : keyId)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA JWK", ex);
        }
    }

    /**
     * 读取 PEM 文件并解码为 DER 字节。
     */
    private byte[] readPem(String location) throws Exception {
        String pem;
        if (location.startsWith("classpath:")) {
            String resourceName = location.substring("classpath:".length()).replaceFirst("^/", "");
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IllegalArgumentException("PEM resource not found: " + location);
                }
                pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            pem = Files.readString(Path.of(location));
        }
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
