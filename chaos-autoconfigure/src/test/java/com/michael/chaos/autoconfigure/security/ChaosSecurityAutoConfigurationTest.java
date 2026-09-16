package com.michael.chaos.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.autoconfigure.audit.ChaosAuditAutoConfiguration;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import com.michael.chaos.security.api.access.AccessSubject;
import com.michael.chaos.security.api.access.AuthorizationManager;
import com.michael.chaos.security.api.access.AuthorizationRequest;
import com.michael.chaos.security.api.access.PermissionAuthorizationService;
import com.michael.chaos.security.api.access.RbacAuthorizationPolicy;
import com.michael.chaos.security.api.auth.LoginUserProvider;
import com.michael.chaos.security.api.datascope.DataScopeAuthorizationService;
import com.michael.chaos.security.config.ChaosSecurityProperties;
import com.michael.chaos.security.permission.PermissionCheckService;
import java.util.Map;
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
}
