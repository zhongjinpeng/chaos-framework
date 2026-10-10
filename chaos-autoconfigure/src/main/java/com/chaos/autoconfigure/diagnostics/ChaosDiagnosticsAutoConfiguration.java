package com.chaos.autoconfigure.diagnostics;

import com.chaos.core.diagnostic.ChaosDiagnostic;
import com.chaos.core.diagnostic.ChaosDiagnosticException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * Chaos 运行诊断自动装配：启动报告、Actuator 健康详情与 Web 运行栈检查。
 *
 * <p>只依赖 Spring Boot 与 chaos-core，任何 chaos 功能库都可以缺失；actuator 端点放在受
 * {@code @ConditionalOnClass} 保护的嵌套配置中。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ChaosDiagnosticsProperties.class)
public class ChaosDiagnosticsAutoConfiguration {

    static final String CHAOS_GATEWAY_MARKER = "com.chaos.gateway.config.ChaosGatewayProperties";

    private static final String HEALTH_DEFAULTS_PROPERTY_SOURCE = "chaosHealthDefaults";

    private static final Map<String, Object> HEALTH_DEFAULTS = Map.of(
            "management.endpoint.health.show-components", "always",
            "management.endpoint.health.show-details", "always",
            "management.health.ping.enabled", false,
            "management.health.refresh.enabled", false,
            "management.health.ssl.enabled", false,
            "spring.cloud.discovery.client.composite-indicator.enabled", false,
            "spring.cloud.discovery.client.health-indicator.enabled", false);

    /**
     * 向 Spring 环境追加低优先级健康检查默认值，业务配置仍可覆盖。
     *
     * <p>该处理器在配置属性 Bean 实例化前注册默认值，使所有 Chaos 应用无需重复声明健康详情与噪声指标配置。</p>
     */
    @Bean
    static BeanFactoryPostProcessor chaosHealthDefaults(ConfigurableEnvironment environment) {
        return beanFactory -> {
            if (!environment.getPropertySources().contains(HEALTH_DEFAULTS_PROPERTY_SOURCE)) {
                environment.getPropertySources().addLast(
                        new MapPropertySource(HEALTH_DEFAULTS_PROPERTY_SOURCE, HEALTH_DEFAULTS));
            }
        };
    }

    /**
     * 注册报告构建器，汇总内置规则与业务自定义的 {@link ChaosDiagnosticRule} Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosFeatureReporter chaosFeatureReporter(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            ApplicationContext applicationContext,
            ChaosDiagnosticsProperties properties,
            ObjectProvider<ChaosDiagnosticRule> customRules,
            ObjectProvider<ChaosStartupIdentifierContributor> identifierContributors) {
        List<ChaosDiagnosticRule> rules = new ArrayList<>(ChaosBuiltInDiagnosticRules.all());
        customRules.orderedStream().forEach(rules::add);
        return new ChaosFeatureReporter(
                beanFactory,
                environment,
                applicationContext,
                rules,
                properties.getStartupReport().getIdentifiers(),
                identifierContributors.orderedStream().toList(),
                properties.getRuntimeHealth().isIncludeConfigurationDetails());
    }

    /**
     * 启动完成后输出一次启动报告。
     */
    @Bean
    @ConditionalOnProperty(prefix = "chaos.diagnostics.startup-report", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ChaosStartupReportLogger chaosStartupReportLogger(
            ApplicationContext applicationContext,
            ChaosFeatureReporter reporter,
            ChaosDiagnosticsProperties properties) {
        return new ChaosStartupReportLogger(applicationContext, reporter, properties.getStartupReport().getLevel());
    }

    /**
     * Servlet 应用中出现 chaos-gateway 时阻断启动。
     *
     * <p>chaos-gateway 只能运行在 WebFlux 上。业务服务与网关的 starter 被同时引入时，Spring Boot 优先选择 Servlet，
     * 网关的鉴权、限流、租户过滤器会被静默跳过——这比启动失败更危险。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = CHAOS_GATEWAY_MARKER)
    @ConditionalOnProperty(prefix = "chaos.diagnostics.web-stack-check", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class WebStackCheckConfiguration {

        /**
         * 在所有单例创建完成后抛出可操作的启动失败信息。
         */
        @Bean
        SmartInitializingSingleton chaosWebStackVerifier() {
            return () -> {
                throw new ChaosDiagnosticException(mixedWebStackDiagnostic());
            };
        }
    }

    /**
     * Actuator 健康检查扩展。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class HealthConfiguration {

        /**
         * 清理默认关闭但已在更早阶段完成自动装配的低价值健康指标。
         */
        @Bean
        static BeanPostProcessor chaosHealthContributorFilter(Environment environment) {
            return new ChaosHealthContributorFilter(environment);
        }

        /**
         * 注册 {@code chaosRuntime} 健康组件，按 Spring Boot 的健康详情策略返回运行配置。
         */
        @Bean
        @ConditionalOnMissingBean
        ChaosRuntimeHealthIndicator chaosRuntimeHealthIndicator(
                ChaosFeatureReporter reporter,
                ApplicationContext applicationContext,
                ObjectProvider<BuildProperties> buildProperties) {
            return new ChaosRuntimeHealthIndicator(
                    reporter, applicationContext, buildProperties.getIfAvailable(), Clock.systemUTC());
        }
    }

    /**
     * Servlet 与 WebFlux 运行栈混用时的诊断信息。
     */
    static ChaosDiagnostic mixedWebStackDiagnostic() {
        return new ChaosDiagnostic(
                "当前是 Servlet 应用，但类路径中存在只能运行在 WebFlux 上的 chaos-gateway",
                List.of(
                        "通常是同时引入了 chaos-gateway-starter 与 chaos-web-service-starter / chaos-web-starter（或 spring-boot-starter-web）",
                        "两者同时存在时 Spring Boot 选择 Servlet，网关的鉴权、限流、租户过滤器不会生效"),
                List.of(
                        "网关服务：移除 chaos-web-service-starter、chaos-web-starter 和 spring-boot-starter-web，只保留 chaos-gateway-starter",
                        "业务服务：移除 chaos-gateway-starter / chaos-gateway 依赖",
                        "确实需要两者共存（例如测试）：设置 chaos.diagnostics.web-stack-check.enabled=false"));
    }
}
