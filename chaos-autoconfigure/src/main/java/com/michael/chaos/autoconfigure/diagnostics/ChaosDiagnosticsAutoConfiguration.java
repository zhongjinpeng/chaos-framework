package com.michael.chaos.autoconfigure.diagnostics;

import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chaos 启动诊断自动装配：启动报告、{@code /actuator/chaos} 端点与 Web 运行栈检查。
 *
 * <p>只依赖 Spring Boot 与 chaos-core，任何 chaos 功能库都可以缺失；actuator 端点放在受
 * {@code @ConditionalOnClass} 保护的嵌套配置中。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ChaosDiagnosticsProperties.class)
public class ChaosDiagnosticsAutoConfiguration {

    static final String CHAOS_GATEWAY_MARKER = "com.michael.chaos.gateway.config.ChaosGatewayProperties";

    /**
     * 注册报告构建器，汇总内置规则与业务自定义的 {@link ChaosDiagnosticRule} Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChaosFeatureReporter chaosFeatureReporter(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            ObjectProvider<ChaosDiagnosticRule> customRules) {
        List<ChaosDiagnosticRule> rules = new ArrayList<>(ChaosBuiltInDiagnosticRules.all());
        customRules.orderedStream().forEach(rules::add);
        return new ChaosFeatureReporter(beanFactory, environment, rules);
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
     * actuator 端点。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    static class EndpointConfiguration {

        /**
         * 注册 {@code /actuator/chaos} 端点。
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint(ChaosEndpoint.class)
        ChaosEndpoint chaosEndpoint(ChaosFeatureReporter reporter) {
            return new ChaosEndpoint(reporter);
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
