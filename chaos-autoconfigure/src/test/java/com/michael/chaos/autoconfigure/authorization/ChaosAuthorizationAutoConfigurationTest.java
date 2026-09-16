package com.michael.chaos.autoconfigure.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.michael.chaos.audit.AuditEventPublisher;
import com.michael.chaos.audit.AuditSupport;
import com.michael.chaos.authorization.captcha.CaptchaController;
import com.michael.chaos.authorization.captcha.CaptchaImageGenerator;
import com.michael.chaos.authorization.captcha.CaptchaService;
import com.michael.chaos.authorization.captcha.CaptchaStore;
import com.michael.chaos.authorization.core.AuthorizationProductionSafetyChecker;
import com.michael.chaos.authorization.core.ChaosAuthorizationProperties;
import com.michael.chaos.authorization.core.RegisteredClientIds;
import com.michael.chaos.authorization.grant.ChaosGrantAuthenticationHandler;
import com.michael.chaos.authorization.kickout.AuthorizationKickoutService;
import com.michael.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.NoopAuthorizationKickoutService;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.token.JwtRevocationService;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import com.michael.chaos.security.redis.authorization.RedisRegisteredClientRepository;
import com.michael.chaos.test.redis.InMemoryRedisTemplates;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 授权服务器自动装配测试。
 */
class ChaosAuthorizationAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosAuthorizationAutoConfiguration.class))
            .withBean("authorizationServerSecurityFilterChain", Object.class, Object::new);

    /**
     * 注册客户端主键必须跨重启稳定。
     *
     * <p>授权记录（Redis/JDBC）里存着签发时的 registeredClientId。主键若每次启动重新随机，
     * 重启后 introspect 与 refresh_token 都会反查不到客户端，把没过期的令牌判成 inactive ——
     * 表现是「重启一次授权服务器，所有人都得重新登录」。
     */
    @Test
    void shouldKeepRegisteredClientIdStableAcrossRestarts() {
        contextRunner
                .withPropertyValues("chaos.authorization.client.id=iam-client")
                .run(first -> {
                    String firstId = first.getBean(RegisteredClientRepository.class)
                            .findByClientId("iam-client").getId();
                    assertThat(firstId).isEqualTo(RegisteredClientIds.stableId("iam-client"));

                    // 再跑一次上下文 = 重启一次进程。
                    contextRunner
                            .withPropertyValues("chaos.authorization.client.id=iam-client")
                            .run(second -> assertThat(second.getBean(RegisteredClientRepository.class)
                                    .findByClientId("iam-client").getId()).isEqualTo(firstId));
                });
    }

    /**
     * store-type=redis 时注册 Redis 客户端仓储，并把配置里的默认客户端写进去。
     */
    @Test
    void shouldRegisterRedisClientRepositoryAndSeedTheDefaultClient() {
        contextRunner
                .withPropertyValues(
                        "chaos.authorization.client.store-type=redis",
                        "chaos.authorization.client.id=iam-client")
                .withBean("redisTemplate", org.springframework.data.redis.core.RedisTemplate.class,
                        InMemoryRedisTemplates::create)
                .run(context -> {
                    RegisteredClientRepository repository =
                            context.getBean(RegisteredClientRepository.class);
                    assertThat(repository).isInstanceOf(RedisRegisteredClientRepository.class);
                    assertThat(repository.findByClientId("iam-client")).isNotNull();
                    assertThat(repository.findById(RegisteredClientIds.stableId("iam-client")))
                            .isNotNull();
                });
    }

    /**
     * 应用注册的自定义 grant 处理器必须出现在默认客户端的授权类型里。
     *
     * <p>否则 token 端点会以 unauthorized_client 拒掉 —— ChaosGrantAuthenticationHandler
     * 这个 SPI 本来就是给应用扩展登录方式用的，客户端不认它等于 SPI 形同虚设。
     */
    @Test
    void shouldAllowGrantTypesContributedByApplicationHandlers() {
        AuthorizationGrantType custom = new AuthorizationGrantType("wechat_ticket");

        contextRunner
                .withPropertyValues("chaos.authorization.client.id=iam-client")
                .withBean("customGrantHandler", ChaosGrantAuthenticationHandler.class,
                        () -> new ChaosGrantAuthenticationHandler() {
                            @Override
                            public AuthorizationGrantType grantType() {
                                return custom;
                            }

                            @Override
                            public LoginUser authenticate(java.util.Map<String, Object> parameters) {
                                throw new UnsupportedOperationException();
                            }
                        })
                .run(context -> {
                    RegisteredClient client = context.getBean(RegisteredClientRepository.class)
                            .findByClientId("iam-client");
                    assertThat(client.getAuthorizationGrantTypes()).contains(custom);
                    // 内置的三种不能因此丢掉。
                    assertThat(client.getAuthorizationGrantTypes())
                            .contains(AuthorizationGrantType.REFRESH_TOKEN);
                });
    }

    /**
     * 本地开发默认配置应注册内存实现，保持示例开箱即用。
     */
    @Test
    void shouldRegisterDevelopmentDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosAuthorizationProperties.class);
            assertThat(context).hasSingleBean(RegisteredClientRepository.class);
            assertThat(context).hasSingleBean(OAuth2AuthorizationService.class);
            assertThat(context).hasSingleBean(OAuth2AuthorizationConsentService.class);
            assertThat(context).hasSingleBean(AuthorizationSessionRegistry.class);
            assertThat(context).hasSingleBean(AuthorizationKickoutService.class);
            assertThat(context).hasSingleBean(JwtRevocationService.class);
            assertThat(context).hasSingleBean(AuthorizationProductionSafetyChecker.class);
            assertThat(context).hasSingleBean(com.michael.chaos.authorization.password.LoginFailureLimiter.class);
            assertThat(context.getBean(RegisteredClientRepository.class))
                    .isInstanceOf(InMemoryRegisteredClientRepository.class);
            assertThat(context.getBean(OAuth2AuthorizationService.class))
                    .isInstanceOf(InMemoryOAuth2AuthorizationService.class);
            assertThat(context.getBean(OAuth2AuthorizationConsentService.class))
                    .isInstanceOf(InMemoryOAuth2AuthorizationConsentService.class);
            assertThat(context.getBean(AuthorizationSessionRegistry.class))
                    .isInstanceOf(InMemoryAuthorizationSessionRegistry.class);
            assertThat(context.getBean(AuthorizationKickoutService.class))
                    .isInstanceOf(NoopAuthorizationKickoutService.class);
            assertThat(context.getBean(JwtRevocationService.class))
                    .isInstanceOf(NoopJwtRevocationService.class);
        });
    }

    /**
     * 开启图形验证码时应完整注册生成、存储、服务和公开端点。
     */
    @Test
    void shouldRegisterCaptchaWhenEnabled() {
        contextRunner
                .withPropertyValues("chaos.authorization.captcha.enabled=true")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaImageGenerator.class);
                    assertThat(context).hasSingleBean(CaptchaStore.class);
                    assertThat(context).hasSingleBean(CaptchaService.class);
                    assertThat(context).hasSingleBean(CaptchaController.class);
                });
    }

    /**
     * 生产模式识别危险内存和 Noop Bean 后必须阻止启动。
     */
    @Test
    void shouldFailOnUnsafeAuthorizationBeansInProductionMode() {
        contextRunner.withPropertyValues("chaos.authorization.production-safety.production-mode=true")
                .run(context -> {
                    AuthorizationProductionSafetyChecker checker =
                            context.getBean(AuthorizationProductionSafetyChecker.class);

                    assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                            .isInstanceOf(IllegalStateException.class);
                });
    }

    /**
     * 迁移期应允许逐项放宽危险默认实现，便于业务分阶段替换。
     */
    @Test
    void shouldAllowUnsafeAuthorizationBeansWhenExplicitlyAllowed() {
        contextRunner.withPropertyValues(
                        "chaos.authorization.production-safety.production-mode=true",
                        "chaos.authorization.production-safety.allow-memory-authorization-store=true",
                        "chaos.authorization.production-safety.allow-noop-jwt-revocation-service=true",
                        "chaos.authorization.production-safety.allow-noop-audit-publisher=true",
                        "chaos.authorization.production-safety.allow-localhost-issuer=true",
                        "chaos.authorization.production-safety.allow-noop-client-secret=true",
                        "chaos.authorization.production-safety.allow-generated-jwk=true"
                )
                .run(context -> {
                    AuthorizationProductionSafetyChecker checker =
                            context.getBean(AuthorizationProductionSafetyChecker.class);

                    assertThatCode(() -> checker.run(new DefaultApplicationArguments()))
                            .doesNotThrowAnyException();
                });
    }

    /**
     * 启用互踢时，生产模式发现本地内存会话索引必须阻止启动。
     */
    @Test
    void shouldFailOnMemorySessionRegistryWhenKickoutEnabledInProductionMode() {
        contextRunner.withPropertyValues(
                        "chaos.authorization.production-safety.production-mode=true",
                        "chaos.authorization.kickout.enabled=true",
                        "chaos.authorization.production-safety.allow-memory-authorization-store=true",
                        "chaos.authorization.production-safety.allow-noop-jwt-revocation-service=true",
                        "chaos.authorization.production-safety.allow-noop-audit-publisher=true"
                )
                .run(context -> {
                    AuthorizationProductionSafetyChecker checker =
                            context.getBean(AuthorizationProductionSafetyChecker.class);

                    assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                            .isInstanceOf(IllegalStateException.class);
                });
    }

    /**
     * 生产安全检查器应在实际 Bean 创建后获取 Bean，避免注册期拿不到目标类型。
     */
    @Test
    void shouldCreateSafetyCheckerWithActualBeanInstances() {
        contextRunner.withPropertyValues("chaos.authorization.production-safety.production-mode=true")
                .withUserConfiguration(ProductionModeConfiguration.class)
                .run(context -> {
                    AuthorizationProductionSafetyChecker checker =
                            context.getBean(AuthorizationProductionSafetyChecker.class);

                    assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                            .isInstanceOf(IllegalStateException.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ProductionModeConfiguration {

        /**
         * 直接调用自动装配方法，验证 ObjectProvider 在 Bean 实例化阶段能解析到实际依赖。
         */
        @Bean
        AuthorizationProductionSafetyChecker checker(
                ChaosAuthorizationProperties properties,
                Environment environment,
                DefaultListableBeanFactory beanFactory) {
            ChaosAuthorizationAutoConfiguration autoConfiguration = new ChaosAuthorizationAutoConfiguration();
            return autoConfiguration.authorizationProductionSafetyChecker(
                    properties,
                    environment,
                    beanFactory.getBeanProvider(RegisteredClientRepository.class),
                    beanFactory.getBeanProvider(OAuth2AuthorizationService.class),
                    beanFactory.getBeanProvider(OAuth2AuthorizationConsentService.class),
                    beanFactory.getBeanProvider(AuthorizationSessionRegistry.class),
                    beanFactory.getBeanProvider(AuthorizationKickoutService.class),
                    beanFactory.getBeanProvider(JwtRevocationService.class),
                    beanFactory.getBeanProvider(AuditEventPublisher.class)
            );
        }
    }
}
