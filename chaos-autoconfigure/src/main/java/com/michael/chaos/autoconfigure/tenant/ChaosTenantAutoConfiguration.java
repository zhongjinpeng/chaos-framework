package com.michael.chaos.autoconfigure.tenant;

import com.michael.chaos.autoconfigure.support.ProductionSafety;
import com.michael.chaos.tenant.NoopTenantStatusProvider;
import com.michael.chaos.tenant.TenantAccessValidator;
import com.michael.chaos.tenant.TenantStatusProvider;
import com.michael.chaos.tenant.servlet.TenantAccessServletFilter;
import com.michael.chaos.core.metrics.ChaosMetrics;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 租户治理自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(TenantStatusProvider.class)
@ConditionalOnProperty(prefix = "chaos.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ChaosTenantProperties.class)
public class ChaosTenantAutoConfiguration {

    /**
     * Spring Security 过滤器链默认顺序为 -100，租户校验必须在认证之后执行才能拿到租户 ID。
     */
    private static final int TENANT_FILTER_ORDER = -90;

    /**
     * 注册默认租户状态提供者。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantStatusProvider tenantStatusProvider() {
        return new NoopTenantStatusProvider();
    }

    /**
     * 生产环境禁止使用默认租户状态提供者（默认阻断启动）。
     */
    @Bean
    @ConditionalOnMissingBean(name = "chaosTenantProductionSafetyChecker")
    public SmartInitializingSingleton chaosTenantProductionSafetyChecker(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory) {
        return () -> ProductionSafety.warnUnsafeDefaultBeans(
                environment,
                beanFactory,
                Map.of(
                        NoopTenantStatusProvider.class.getName(),
                        "chaos-tenant: 生产环境不能使用 NoopTenantStatusProvider，请接入真实租户中心或租户仓储"
                )
        );
    }

    /**
     * 注册默认租户访问校验器。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantAccessValidator tenantAccessValidator(
            TenantStatusProvider tenantStatusProvider,
            ChaosTenantProperties properties) {
        return new TenantAccessValidator(tenantStatusProvider, properties.isFailClosed());
    }

    /**
     * Servlet 服务租户状态校验。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    @ConditionalOnProperty(prefix = "chaos.tenant.servlet-filter", name = "enabled", havingValue = "true")
    static class TenantServletFilterConfiguration {

        /**
         * 注册租户状态校验过滤器。
         */
        @Bean
        FilterRegistrationBean<TenantAccessServletFilter> tenantAccessServletFilter(
                TenantAccessValidator validator,
                ChaosTenantProperties properties,
                ObjectProvider<ChaosMetrics> metricsProvider) {
            FilterRegistrationBean<TenantAccessServletFilter> registration = new FilterRegistrationBean<>(
                    new TenantAccessServletFilter(validator, properties.getServletFilter().getExcludePaths(),
                            metricsProvider.getIfAvailable()));
            registration.setOrder(TENANT_FILTER_ORDER);
            return registration;
        }
    }
}
