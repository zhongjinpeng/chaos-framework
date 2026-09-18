package com.michael.chaos.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.access.AccessExpressionEvaluator;
import com.michael.chaos.security.access.AccessSubjectFactory;
import com.michael.chaos.security.access.PermissionResolver;
import com.michael.chaos.security.access.RequireAccessAspect;
import com.michael.chaos.security.access.env.RequestContextContributor;
import com.michael.chaos.security.access.env.ServletRequestContextContributor;
import com.michael.chaos.security.access.env.TimeContextContributor;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationResource;
import com.michael.chaos.security.api.access.CompositeAuthorizationPolicy;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.access.RbacAuthorizationPolicy;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import com.michael.chaos.security.api.datascope.DataScopeAuthorizationService;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import com.michael.chaos.security.permission.PermissionCheckService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全自动装配测试。
 */
class ChaosSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ChaosAuditAutoConfiguration.class,
                    ChaosSecurityAutoConfiguration.class
            ));

    /**
     * 资源服务器默认应关闭 HTTP Basic；非 Web 上下文不应创建 servlet 过滤器链。
     */
    @Test
    void httpBasicShouldBeDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosSecurityProperties.class);
            ChaosSecurityProperties properties = context.getBean(ChaosSecurityProperties.class);
            assertThat(properties.isHttpBasicEnabled()).isFalse();
            assertThat(properties.getPermitAll()).containsExactly("/actuator/health");
            assertThat(properties.getToken().getType()).isEqualTo(ChaosSecurityProperties.TokenType.JWT);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).hasSingleBean(RbacAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(AuthorizationManager.class);
            assertThat(context).hasSingleBean(PermissionAuthorizationService.class);
            assertThat(context).hasSingleBean(DataScopeAuthorizationService.class);
            assertThat(context).hasSingleBean(PermissionCheckService.class);
            assertThat(context).hasSingleBean(LoginUserProvider.class);
        });
    }

    /**
     * 迁移期应允许显式开启 HTTP Basic。
     */
    @Test
    void httpBasicCanBeEnabledExplicitly() {
        contextRunner.withPropertyValues("chaos.security.http-basic-enabled=true")
                .run(context -> assertThat(context.getBean(ChaosSecurityProperties.class).isHttpBasicEnabled()).isTrue());
    }

    /**
     * 默认 RBAC admin 角色应支持配置覆盖。
     */
    @Test
    void rbacAdminRolesCanBeConfigured() {
        contextRunner.withPropertyValues("chaos.security.access.admin-roles[0]=super_admin")
                .run(context -> {
                    AuthorizationManager manager = context.getBean(AuthorizationManager.class);
                    AccessSubject subject = new AccessSubject(
                            "1001",
                            "alice",
                            "tenant-a",
                            Set.of("super_admin"),
                            Set.of(),
                            Map.of()
                    );

                    assertThat(manager.isAllowed(AuthorizationRequest.of(subject, "order:delete"))).isTrue();
                });
    }

    /**
     * 生产模式发现空 JWT 撤销服务时默认阻断启动，避免注销后的 token 在生产环境继续有效。
     */
    @Test
    void shouldRejectNoopJwtRevocationServiceInProductionMode() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 显式关闭 fail-fast 后只告警，保留灰度迁移期间的逃生开关。
     */
    @Test
    void shouldOnlyWarnAboutNoopJwtRevocationServiceWhenFailFastDisabled() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.fail-fast=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChaosSecurityProperties.class);
                });
    }

    /**
     * opaque introspection 缺少凭据时给出具体配置项。
     */
    @Test
    void shouldExplainMissingOpaqueCredentials() {
        contextRunner.withPropertyValues("chaos.security.opaque-token.introspection-uri=http://auth:9000/oauth2/introspect")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ChaosDiagnosticException.class)
                        .hasMessageStartingWith("问题：资源服务 opaque token introspection 缺少客户端凭据")
                        .hasMessageContaining("chaos.security.opaque-token.client-secret"));
    }

    /**
     * 默认上下文应注册全套 RBAC/ABAC 组件；非 Web 上下文不注册 Servlet 环境属性贡献者。
     */
    @Test
    void shouldRegisterAccessControlBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CompositeAuthorizationPolicy.class);
            assertThat(context).hasSingleBean(PermissionResolver.class);
            assertThat(context).hasSingleBean(AccessSubjectFactory.class);
            assertThat(context).hasSingleBean(AccessExpressionEvaluator.class);
            assertThat(context).hasSingleBean(RequireAccessAspect.class);
            assertThat(context).hasSingleBean(TimeContextContributor.class);
            assertThat(context).hasSingleBean(RequestContextContributor.class);
            assertThat(context).doesNotHaveBean(ServletRequestContextContributor.class);
            assertThat(context.getBean(CompositeAuthorizationPolicy.class).size()).isZero();
        });
    }

    /**
     * 配置的 ABAC DENY 策略应能否决 RBAC 的放行。
     */
    @Test
    void configuredAbacPolicyShouldVetoRbacAllow() {
        contextRunner.withPropertyValues(
                        "chaos.security.access.policies[0].id=deny-external-network",
                        "chaos.security.access.policies[0].effect=DENY",
                        "chaos.security.access.policies[0].actions[0]=order:read",
                        "chaos.security.access.policies[0].conditions[0].left=environment.network",
                        "chaos.security.access.policies[0].conditions[0].operator=EQ",
                        "chaos.security.access.policies[0].conditions[0].values[0]=external")
                .run(context -> {
                    AuthorizationManager manager = context.getBean(AuthorizationManager.class);
                    AccessSubject subject = subject(Set.of("user"), Set.of("order:read"));

                    assertThat(manager.isAllowed(AuthorizationRequest.of(subject, "order:read"))).isTrue();
                    assertThat(manager.decide(AuthorizationRequest.of(
                            subject,
                            "order:read",
                            AuthorizationResource.NONE,
                            Map.of("network", "external"))).policyId()).isEqualTo("deny-external-network");
                });
    }

    /**
     * 组内合并算法只影响配置策略之间的关系。
     */
    @Test
    void combiningAlgorithmShouldApplyAmongConfiguredPolicies() {
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "chaos.security.access.policies[0].id=deny-weekend",
                "chaos.security.access.policies[0].effect=DENY",
                "chaos.security.access.policies[0].actions[0]=order:read",
                "chaos.security.access.policies[0].conditions[0].left=environment.dayOfWeek",
                "chaos.security.access.policies[0].conditions[0].operator=IN",
                "chaos.security.access.policies[0].conditions[0].values[0]=SATURDAY",
                "chaos.security.access.policies[1].id=allow-support",
                "chaos.security.access.policies[1].actions[0]=order:read",
                "chaos.security.access.policies[1].conditions[0].left=subject.roles",
                "chaos.security.access.policies[1].conditions[0].operator=IN",
                "chaos.security.access.policies[1].conditions[0].values[0]=support");
        AccessSubject support = subject(Set.of("support"), Set.of());
        AuthorizationRequest weekendRequest = AuthorizationRequest.of(
                support,
                "order:read",
                AuthorizationResource.NONE,
                Map.of("dayOfWeek", "SATURDAY"));

        runner.run(context -> assertThat(context.getBean(AuthorizationManager.class).isAllowed(weekendRequest))
                .isFalse());
        runner.withPropertyValues("chaos.security.access.combining-algorithm=ALLOW_OVERRIDES")
                .run(context -> assertThat(context.getBean(AuthorizationManager.class).isAllowed(weekendRequest))
                        .isTrue());
    }

    /**
     * 角色继承与权限通配应可通过配置开启。
     */
    @Test
    void roleHierarchyAndWildcardPermissionShouldApply() {
        contextRunner.withPropertyValues("chaos.security.access.role-hierarchy.root[0]=admin")
                .run(context -> {
                    AuthorizationManager manager = context.getBean(AuthorizationManager.class);

                    assertThat(manager.isAllowed(AuthorizationRequest.of(
                            subject(Set.of("root"), Set.of()), "order:delete"))).isTrue();
                    assertThat(manager.isAllowed(AuthorizationRequest.of(
                            subject(Set.of("user"), Set.of("order:*")), "order:read"))).isTrue();
                });
    }

    /**
     * 关闭通配后，order:* 不再覆盖 order:read。
     */
    @Test
    void wildcardPermissionCanBeDisabled() {
        contextRunner.withPropertyValues("chaos.security.access.wildcard-permission-enabled=false")
                .run(context -> assertThat(context.getBean(AuthorizationManager.class).isAllowed(
                        AuthorizationRequest.of(subject(Set.of("user"), Set.of("order:*")), "order:read")))
                        .isFalse());
    }

    /**
     * 策略配置写错时启动失败，并给出配置路径与改法。
     */
    @Test
    void invalidPolicyShouldFailStartupWithDiagnostic() {
        contextRunner.withPropertyValues(
                        "chaos.security.access.policies[0].id=bad",
                        "chaos.security.access.policies[0].actions[0]=order:read",
                        "chaos.security.access.policies[0].conditions[0].left=subject.tenantId",
                        "chaos.security.access.policies[0].conditions[0].operator=IN")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ChaosDiagnosticException.class)
                        .hasMessageContaining("缺少比较值")
                        .hasMessageContaining("chaos.security.access.policies[0].conditions[0].values"));
    }

    /**
     * 自定义权限解析器补全的权限，对 @Permission 与 SecurityUtils 同样生效。
     */
    @Test
    void customPermissionResolverShouldApplyToPermissionChecks() {
        contextRunner.withBean(PermissionResolver.class, () -> user -> Set.of("order:read"))
                .run(context -> {
                    LoginUser user = new LoginUser("1001", "alice", "tenant-a", Set.of("user"), Set.of());

                    assertThat(context.getBean(PermissionAuthorizationService.class)
                            .hasPermission(Optional.of(user), "order:read")).isTrue();
                    assertThat(context.getBean(PermissionCheckService.class)
                            .hasPermission(Optional.of(user), "order:delete")).isFalse();
                });
    }

    /**
     * 业务侧自定义的授权组件应覆盖框架默认实现。
     */
    @Test
    void customAccessBeansShouldBackOffFrameworkDefaults() {
        contextRunner
                .withBean(AuthorizationManager.class, () -> request -> AuthorizationDecision.allow("custom", "test"))
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthorizationManager.class);
                    assertThat(context.getBean(AuthorizationManager.class).decide(
                            AuthorizationRequest.of(AccessSubject.ANONYMOUS, "anything")).policyId())
                            .isEqualTo("custom");
                });
    }

    private AccessSubject subject(Set<String> roles, Set<String> permissions) {
        return new AccessSubject("1001", "alice", "tenant-a", roles, permissions, Map.of());
    }
}
