package com.michael.chaos.authorization.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.audit.NoopAuditEventPublisher;
import com.michael.chaos.authorization.kickout.InMemoryAuthorizationSessionRegistry;
import com.michael.chaos.authorization.kickout.NoopAuthorizationKickoutService;
import com.michael.chaos.core.constant.ProductionProfiles;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.token.NoopJwtRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 授权服务器生产安全检查测试。
 */
class AuthorizationProductionSafetyCheckerTest {

    /**
     * 生产 profile 下使用默认开发配置（{noop} 密钥、临时 JWK、localhost issuer）时必须阻止启动。
     */
    @Test
    void shouldFailWhenProductionUsesDevelopmentDefaults() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client.secret")
                .hasMessageContaining("jwk");
    }

    /**
     * 失败信息使用"问题 / 原因 / 怎么修"格式，并给出放行开关。
     */
    @Test
    void failureMessageShouldBeActionable() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageStartingWith("问题：授权服务器生产安全检查未通过")
                .hasMessageContaining("怎么修：")
                .hasMessageContaining("chaos.authorization.production-safety.fail-fast=false");
    }

    /**
     * prd 与全局生产安全检查一致，默认被识别为生产 profile。
     */
    @Test
    void prdProfileShouldBeTreatedAsProductionByDefault() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prd");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThat(properties.getProductionSafety().getProfiles())
                .containsExactlyElementsOf(ProductionProfiles.DEFAULTS);
        assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 关闭 fail-fast 后违规项只告警，便于迁移期过渡。
     */
    @Test
    void shouldOnlyWarnWhenFailFastDisabled() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        properties.getProductionSafety().setProductionMode(true);
        properties.getProductionSafety().setFailFast(false);
        MockEnvironment environment = new MockEnvironment();
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 显式生产模式下即使未命中生产 profile，也必须执行安全检查。
     */
    @Test
    void shouldFailWhenProductionModeIsExplicitlyEnabled() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        properties.getProductionSafety().setProductionMode(true);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 显式注入实际 Bean 时，生产检查必须识别内存和 Noop 实现并阻止启动。
     */
    @Test
    void shouldFailWhenProductionUsesUnsafeBeanImplementations() {
        ChaosAuthorizationProperties properties = productionSafeProperties();
        properties.getProductionSafety().setProductionMode(true);
        properties.getKickout().setEnabled(true);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(
                properties,
                environment,
                new InMemoryRegisteredClientRepository(registeredClient()),
                new InMemoryOAuth2AuthorizationService(),
                new InMemoryOAuth2AuthorizationConsentService(),
                new InMemoryAuthorizationSessionRegistry(),
                new NoopAuthorizationKickoutService(),
                new NoopJwtRevocationService(),
                new NoopAuditEventPublisher()
        );

        assertThatThrownBy(() -> checker.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NoopJwtRevocationService")
                .hasMessageContaining("InMemoryOAuth2AuthorizationService");
    }

    /**
     * Bean 级迁移开关应允许分阶段替换内存和 Noop 实现。
     */
    @Test
    void shouldAllowUnsafeBeanImplementationsWhenExplicitlyAllowed() {
        ChaosAuthorizationProperties properties = productionSafeProperties();
        properties.getProductionSafety().setProductionMode(true);
        properties.getProductionSafety().setAllowMemoryClientStore(true);
        properties.getProductionSafety().setAllowMemoryAuthorizationStore(true);
        properties.getProductionSafety().setAllowMemorySessionRegistry(true);
        properties.getProductionSafety().setAllowNoopKickoutService(true);
        properties.getProductionSafety().setAllowNoopJwtRevocationService(true);
        properties.getProductionSafety().setAllowNoopAuditPublisher(true);
        properties.getKickout().setEnabled(true);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(
                properties,
                environment,
                new InMemoryRegisteredClientRepository(registeredClient()),
                new InMemoryOAuth2AuthorizationService(),
                new InMemoryOAuth2AuthorizationConsentService(),
                new InMemoryAuthorizationSessionRegistry(),
                new NoopAuthorizationKickoutService(),
                new NoopJwtRevocationService(),
                new NoopAuditEventPublisher()
        );

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 内存客户端仓储会导致重启丢失注册信息，但不应阻断已经能够正常提供服务的实例启动。
     */
    @Test
    void shouldNotFailFastWhenProductionUsesMemoryClientStore() {
        ChaosAuthorizationProperties properties = productionSafeProperties();
        properties.getProductionSafety().setProductionMode(true);
        properties.getClient().setStoreType(ChaosAuthorizationProperties.ClientStoreType.MEMORY);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 显式关闭生产模式时，即使命中 prod profile 也跳过安全检查，便于迁移期按应用控制。
     */
    @Test
    void shouldSkipWhenProductionModeIsExplicitlyDisabled() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        properties.getProductionSafety().setProductionMode(false);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 非生产 profile 下允许保留开发默认配置，保证本地示例开箱即用。
     */
    @Test
    void shouldSkipWhenProfileIsNotProduction() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 生产 profile 下配置固定 issuer、JDBC client、加密密钥和固定 JWK 后应通过检查。
     */
    @Test
    void shouldPassWhenProductionConfigurationIsExplicit() {
        ChaosAuthorizationProperties properties = productionSafeProperties();
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "production");
        AuthorizationProductionSafetyChecker checker = new AuthorizationProductionSafetyChecker(properties, environment);

        assertThatCode(() -> checker.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    /**
     * 嵌套配置被设置为 null 时恢复默认对象，避免自动装配阶段出现 NPE。
     */
    @Test
    void shouldRestoreDefaultNestedPropertiesWhenSetToNull() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();

        properties.setToken(null);
        properties.setRefreshToken(null);
        properties.setKickout(null);
        properties.setGrant(null);
        properties.setJwk(null);
        properties.setClient(null);
        properties.setProductionSafety(null);

        assertThat(properties.getToken()).isNotNull();
        assertThat(properties.getRefreshToken()).isNotNull();
        assertThat(properties.getKickout()).isNotNull();
        assertThat(properties.getGrant()).isNotNull();
        assertThat(properties.getJwk()).isNotNull();
        assertThat(properties.getClient()).isNotNull();
        assertThat(properties.getProductionSafety()).isNotNull();
    }

    /**
     * 枚举配置被设置为 null 时恢复安全默认值，避免 switch 或条件判断空指针。
     */
    @Test
    void shouldRestoreDefaultEnumsWhenSetToNull() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();

        properties.getToken().setType(null);
        properties.getKickout().setScope(null);
        properties.getKickout().setSessionRegistryType(null);
        properties.getClient().setStoreType(null);

        assertThat(properties.getToken().getType()).isEqualTo(ChaosAuthorizationProperties.TokenType.JWT);
        assertThat(properties.getKickout().getScope()).isEqualTo(ChaosAuthorizationProperties.Scope.CLIENT);
        assertThat(properties.getKickout().getSessionRegistryType())
                .isEqualTo(ChaosAuthorizationProperties.SessionRegistryType.AUTO);
        assertThat(properties.getClient().getStoreType())
                .isEqualTo(ChaosAuthorizationProperties.ClientStoreType.MEMORY);
    }

    private ChaosAuthorizationProperties productionSafeProperties() {
        ChaosAuthorizationProperties properties = new ChaosAuthorizationProperties();
        properties.setIssuer("https://auth.example.com");
        properties.getClient().setStoreType(ChaosAuthorizationProperties.ClientStoreType.JDBC);
        properties.getClient().setSecret("{bcrypt}$2a$10$abcdefghijklmnopqrstuu1Qw7.6o2dRuV8O/2v7z4QpYrlQUYJjK");
        properties.getJwk().setPublicKeyLocation("/etc/chaos/auth-public.pem");
        properties.getJwk().setPrivateKeyLocation("/etc/chaos/auth-private.pem");
        return properties;
    }

    private RegisteredClient registeredClient() {
        return RegisteredClient.withId("client-id")
                .clientId("chaos-client")
                .clientSecret("{noop}chaos-secret")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
    }
}
