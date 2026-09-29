package com.chaos.autoconfigure.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.chaos.audit.AuditEventPublisher;
import com.chaos.authorization.captcha.CaptchaController;
import com.chaos.authorization.captcha.CaptchaImageGenerator;
import com.chaos.authorization.captcha.CaptchaService;
import com.chaos.authorization.captcha.CaptchaStore;
import com.chaos.authorization.core.AuthorizationProductionSafetyChecker;
import com.chaos.authorization.core.ChaosAuthorizationProperties;
import com.chaos.authorization.core.RegisteredClientIds;
import com.chaos.authorization.kickout.AuthorizationKickoutService;
import com.chaos.authorization.kickout.AuthorizationSessionRegistry;
import com.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import com.chaos.authorization.kickout.NoopAuthorizationKickoutService;
import com.chaos.security.api.token.JwtRevocationService;
import com.chaos.security.api.token.NoopJwtRevocationService;
import com.chaos.security.redis.authorization.RedisRegisteredClientRepository;
import com.chaos.test.redis.InMemoryRedisTemplates;
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
            .withPropertyValues(
                    "chaos.authorization.client.registrations.management.secret={noop}management-secret")
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
                .withPropertyValues(
                        "chaos.authorization.client.registrations.iam.secret={noop}iam-secret")
                .run(first -> {
                    String firstId = first.getBean(RegisteredClientRepository.class)
                            .findByClientId("iam").getId();
                    assertThat(firstId).isEqualTo(RegisteredClientIds.stableId("iam"));

                    // 再跑一次上下文 = 重启一次进程。
                    contextRunner
                            .withPropertyValues(
                                    "chaos.authorization.client.registrations.iam.secret={noop}iam-secret")
                            .run(second -> assertThat(second.getBean(RegisteredClientRepository.class)
                                    .findByClientId("iam").getId()).isEqualTo(firstId));
                });
    }

    /**
     * Chaos 配置中的多个客户端和授权类型必须由同一个仓储统一注册。
     */
    @Test
    void shouldRegisterMultipleStandardClientsWithConfiguredGrantTypes() {
        contextRunner
                .withPropertyValues(
                        "chaos.authorization.client.registrations.management.secret={noop}management-secret",
                        "chaos.authorization.client.registrations.management.grant-types[0]=password",
                        "chaos.authorization.client.registrations.management.grant-types[1]=refresh_token",
                        "chaos.authorization.client.registrations.management.grant-types[2]=client_credentials",
                        "chaos.authorization.client.registrations.management.scopes[0]=read",
                        "chaos.authorization.client.registrations.web.secret={noop}web-secret",
                        "chaos.authorization.client.registrations.web.grant-types[0]=password",
                        "chaos.authorization.client.registrations.web.grant-types[1]=refresh_token",
                        "chaos.authorization.client.registrations.web.scopes[0]=read")
                .run(context -> {
                    RegisteredClientRepository repository = context.getBean(RegisteredClientRepository.class);
                    RegisteredClient management = repository.findByClientId("management");
                    RegisteredClient web = repository.findByClientId("web");

                    assertThat(management).isNotNull();
                    assertThat(management.getId()).isEqualTo(RegisteredClientIds.stableId("management"));
                    assertThat(management.getAuthorizationGrantTypes()).contains(
                            new AuthorizationGrantType("password"),
                            AuthorizationGrantType.REFRESH_TOKEN,
                            AuthorizationGrantType.CLIENT_CREDENTIALS);
                    assertThat(web).isNotNull();
                    assertThat(web.getId()).isEqualTo(RegisteredClientIds.stableId("web"));
                    assertThat(repository.findByClientId("chaos-client")).isNull();
                });
    }

    /**
     * store-type=redis 时注册 Redis 客户端仓储，并把 registrations 中的客户端写进去。
     */
    @Test
    void shouldRegisterRedisClientRepositoryAndSeedConfiguredClients() {
        contextRunner
                .withPropertyValues(
                        "chaos.authorization.client.store-type=redis",
                        "chaos.authorization.client.registrations.redis-client.secret={noop}redis-secret")
                .withBean("redisTemplate", org.springframework.data.redis.core.RedisTemplate.class,
                        InMemoryRedisTemplates::create)
                .run(context -> {
                    RegisteredClientRepository repository =
                            context.getBean(RegisteredClientRepository.class);
                    assertThat(repository).isInstanceOf(RedisRegisteredClientRepository.class);
                    assertThat(repository.findByClientId("redis-client")).isNotNull();
                    assertThat(repository.findById(RegisteredClientIds.stableId("redis-client")))
                            .isNotNull();
                });
    }

    /**
     * 客户端配置中声明的自定义 grant 必须出现在该客户端的授权类型里。
     *
     * <p>否则 token 端点会以 unauthorized_client 拒掉。grant handler 只负责处理请求，
     * 是否允许使用仍由客户端 registrations 配置决定。
     */
    @Test
    void shouldRegisterConfiguredCustomGrantTypes() {
        AuthorizationGrantType custom = new AuthorizationGrantType("wechat_ticket");

        contextRunner
                .withPropertyValues(
                        "chaos.authorization.client.registrations.custom.secret={noop}custom-secret",
                        "chaos.authorization.client.registrations.custom.grant-types[0]=wechat_ticket")
                .run(context -> {
                    RegisteredClient client = context.getBean(RegisteredClientRepository.class)
                            .findByClientId("custom");
                    assertThat(client.getAuthorizationGrantTypes()).contains(custom);
                });
    }

    /**
     * 未声明客户端时必须快速失败，不能隐式生成 chaos-client。
     */
    @Test
    void shouldFailWhenNoClientRegistrationIsConfigured() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosAuthorizationAutoConfiguration.class))
                .withBean("authorizationServerSecurityFilterChain", Object.class, Object::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("client.registrations");
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
            assertThat(context).hasSingleBean(com.chaos.authorization.password.LoginFailureLimiter.class);
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
