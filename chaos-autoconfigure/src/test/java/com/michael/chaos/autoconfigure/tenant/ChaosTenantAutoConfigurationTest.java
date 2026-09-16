package com.michael.chaos.autoconfigure.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.tenant.NoopTenantStatusProvider;
import com.michael.chaos.tenant.TenantAccessValidator;
import com.michael.chaos.tenant.TenantContext;
import com.michael.chaos.tenant.TenantDescriptor;
import com.michael.chaos.tenant.TenantIsolationMode;
import com.michael.chaos.tenant.TenantPlan;
import com.michael.chaos.tenant.TenantStatus;
import com.michael.chaos.tenant.TenantStatusProvider;
import com.michael.chaos.tenant.servlet.TenantAccessServletFilter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 租户治理自动装配测试。
 */
class ChaosTenantAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosTenantAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        TenantContext.clear();
    }

    /**
     * 默认应注册租户状态端口和访问校验器。
     */
    @Test
    void shouldRegisterDefaultTenantBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosTenantProperties.class);
            assertThat(context).hasSingleBean(TenantStatusProvider.class);
            assertThat(context).hasSingleBean(TenantAccessValidator.class);
        });
    }

    /**
     * 业务自定义租户状态提供者时，默认实现应让位。
     */
    @Test
    void shouldBackOffWhenCustomTenantStatusProviderProvided() {
        contextRunner.withUserConfiguration(CustomTenantConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(TenantStatusProvider.class));
    }

    /**
     * 关闭租户治理时不注册默认 Bean。
     */
    @Test
    void shouldDisableTenantAutoConfiguration() {
        contextRunner.withPropertyValues("chaos.tenant.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TenantStatusProvider.class);
                    assertThat(context).doesNotHaveBean(TenantAccessValidator.class);
                });
    }

    /**
     * 生产模式发现空租户状态提供者时默认阻断启动。
     */
    @Test
    void shouldRejectNoopTenantStatusProviderInProduction() {
        contextRunner.withPropertyValues("chaos.production-safety.production-mode=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("NoopTenantStatusProvider"));
    }

    /**
     * 迁移期可以显式放行。
     */
    @Test
    void shouldAllowNoopTenantStatusProviderWithMigrationOverride() {
        contextRunner.withPropertyValues(
                        "chaos.production-safety.production-mode=true",
                        "chaos.production-safety.allow-unsafe-defaults=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TenantStatusProvider.class))
                            .isInstanceOf(NoopTenantStatusProvider.class);
                });
    }

    /**
     * Servlet 租户校验过滤器默认关闭，显式开启后注册。
     */
    @Test
    void shouldRegisterServletFilterOnlyWhenEnabled() {
        WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosTenantAutoConfiguration.class));
        webRunner.run(context -> assertThat(context).doesNotHaveBean("tenantAccessServletFilter"));
        webRunner.withPropertyValues("chaos.tenant.servlet-filter.enabled=true")
                .run(context -> assertThat(context).hasBean("tenantAccessServletFilter"));
    }

    /**
     * 冻结租户被拒绝；正常租户写入 TenantContext 并在请求结束后清理。
     */
    @Test
    void servletFilterShouldValidateTenantStatusAndClearContext() throws Exception {
        TenantStatusProvider provider = tenantId -> "frozen".equals(tenantId)
                ? new TenantDescriptor(tenantId, TenantStatus.FROZEN, TenantPlan.empty(), TenantIsolationMode.SHARED_SCHEMA)
                : TenantDescriptor.active(tenantId);
        TenantAccessServletFilter filter = new TenantAccessServletFilter(
                new TenantAccessValidator(provider, true), List.of("/actuator/**"));

        RequestContext.setTenantId("frozen");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), denied, (req, res) -> { });
        assertThat(denied.getStatus()).isEqualTo(403);

        RequestContext.setTenantId("tenant-a");
        AtomicReference<String> observed = new AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(),
                (req, res) -> observed.set(TenantContext.current().map(TenantDescriptor::tenantId).orElse("")));
        assertThat(observed).hasValue("tenant-a");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTenantConfiguration {

        /**
         * 业务自定义租户状态提供者。
         */
        @Bean
        TenantStatusProvider tenantStatusProvider() {
            return tenantId -> null;
        }
    }
}
