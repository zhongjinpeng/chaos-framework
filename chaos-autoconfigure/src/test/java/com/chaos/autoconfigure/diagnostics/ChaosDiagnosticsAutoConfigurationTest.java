package com.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Severity;
import com.chaos.autoconfigure.gateway.ChaosGatewayAutoConfiguration;
import com.chaos.autoconfigure.job.ChaosJobAutoConfiguration;
import com.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * 启动诊断自动装配测试。
 */
@ExtendWith(OutputCaptureExtension.class)
class ChaosDiagnosticsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=diagnostics-demo")
            .withConfiguration(AutoConfigurations.of(
                    ChaosDiagnosticsAutoConfiguration.class,
                    ChaosJobAutoConfiguration.class,
                    ChaosGatewayAutoConfiguration.class));

    /**
     * 报告区分已启用、应用类型不符、被配置关闭与未导入，并附带关键配置。
     */
    @Test
    void reportShouldExplainEnabledAndDisabledFeatures() {
        contextRunner.run(context -> {
            ChaosFeatureReport report = context.getBean(ChaosFeatureReporter.class).build();

            assertThat(report.application()).isEqualTo("diagnostics-demo");
            assertThat(feature(report, "job").enabled()).isTrue();
            assertThat(feature(report, "job").settings()).containsEntry("scheduling-enabled", "true");
            assertThat(feature(report, "gateway").category()).isEqualTo(DisabledCategory.WEB_APPLICATION_TYPE);
            assertThat(feature(report, "mybatis").category()).isEqualTo(DisabledCategory.NOT_IMPORTED);
        });
        contextRunner.withPropertyValues("chaos.job.enabled=false").run(context -> {
            Feature job = feature(context.getBean(ChaosFeatureReporter.class).build(), "job");
            assertThat(job.enabled()).isFalse();
            assertThat(job.category()).isEqualTo(DisabledCategory.DISABLED_BY_PROPERTY);
            assertThat(job.reason()).contains("chaos.job.enabled");
        });
    }

    /**
     * 启动后以 INFO 输出一次紧凑的报告表格。
     */
    @Test
    void shouldLogStartupReportOnce(CapturedOutput output) {
        contextRunner.run(context -> context.publishEvent(
                new ContextRefreshedEvent(context.getSourceApplicationContext())));

        assertThat(output).contains("Chaos 启动报告 | 应用 diagnostics-demo");
        assertThat(output).doesNotContain(
                "已启用（", "未启用", "诊断（0）", "YAML 最终生效配置", "系统环境变量", "JVM 系统属性");
        assertThat(output.toString().split("Chaos 启动报告", -1)).hasSize(2);
    }

    /**
     * 可以关闭启动报告，或降为 DEBUG 级别。
     */
    @Test
    void startupReportCanBeDisabledOrLowered(CapturedOutput output) {
        contextRunner.withPropertyValues("chaos.diagnostics.startup-report.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ChaosStartupReportLogger.class));
        contextRunner.withPropertyValues("chaos.diagnostics.startup-report.level=debug")
                .run(context -> assertThat(context).hasSingleBean(ChaosStartupReportLogger.class));

        assertThat(output).doesNotContain("Chaos 启动报告");
    }

    /**
     * 健康组件默认返回配置明细，也可以通过统一开关整体关闭。
     */
    @Test
    void configurationDetailsShouldBeEnabledByDefaultAndCanBeDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChaosRuntimeHealthIndicator.class);
            Map<String, Object> runtime = healthRuntime(context.getBean(ChaosRuntimeHealthIndicator.class));
            assertThat((Map<?, ?>) runtime.get("systemEnvironment")).isNotEmpty();
            assertThat((Map<?, ?>) runtime.get("jvmSystemProperties")).isNotEmpty();
        });
        contextRunner.withPropertyValues(
                        "chaos.diagnostics.runtime-health.include-configuration-details=false")
                .run(context -> {
                    Map<String, Object> runtime = healthRuntime(context.getBean(ChaosRuntimeHealthIndicator.class));
                    assertThat(runtime).doesNotContainKeys(
                            "configurationSources",
                            "effectiveYamlConfiguration",
                            "systemEnvironment",
                            "jvmSystemProperties");
                });
    }

    /**
     * 框架统一提供健康详情和噪声指标默认值，业务配置可以按需覆盖。
     */
    @Test
    void healthDefaultsShouldBeProvidedByFrameworkAndRemainOverridable() {
        contextRunner.run(context -> {
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-components"))
                    .isEqualTo("always");
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("always");
            assertThat(context.getEnvironment().getProperty("management.health.ping.enabled", Boolean.class))
                    .isFalse();
            assertThat(context.getEnvironment().getProperty(
                            "spring.cloud.discovery.client.health-indicator.enabled", Boolean.class))
                    .isFalse();
        });

        contextRunner
                .withPropertyValues(
                        "management.endpoint.health.show-details=never",
                        "management.health.ping.enabled=true")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details"))
                            .isEqualTo("never");
                    assertThat(context.getEnvironment().getProperty(
                                    "management.health.ping.enabled", Boolean.class))
                            .isTrue();
                });
    }

    /**
     * 启动报告不能输出凭据。
     */
    @Test
    void startupReportShouldNotLeakSecrets(CapturedOutput output) {
        contextRunner.withUserConfiguration(SecretSettingRuleConfiguration.class)
                .withPropertyValues(
                        "chaos.gateway.opaque-token.client-secret=super-secret-value",
                        "spring.datasource.password=db-password-value")
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output).contains("Chaos 启动报告");
        assertThat(output).doesNotContain("super-secret-value").doesNotContain("db-password-value");
    }

    /**
     * 业务自定义规则会被汇总到报告中，异常规则被忽略。
     */
    @Test
    void customRulesShouldBeIncludedAndFailuresIgnored() {
        contextRunner.withUserConfiguration(CustomRuleConfiguration.class).run(context -> {
            List<Finding> findings = context.getBean(ChaosFeatureReporter.class).build().findings();
            assertThat(findings).extracting(Finding::problem).contains("custom-problem");
        });
    }

    /**
     * Servlet 应用中出现 chaos-gateway 时阻断启动，并说明应该移除哪个 starter。
     */
    @Test
    void servletApplicationWithGatewayLibraryShouldFailFast() {
        WebApplicationContextRunner servletRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosDiagnosticsAutoConfiguration.class));

        servletRunner.run(context -> assertThat(context).hasFailed()
                .getFailure()
                .isInstanceOf(ChaosDiagnosticException.class)
                .hasMessageStartingWith("问题：当前是 Servlet 应用，但类路径中存在只能运行在 WebFlux 上的 chaos-gateway")
                .hasMessageContaining("chaos-gateway-starter"));
        servletRunner.withPropertyValues("chaos.diagnostics.web-stack-check.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 响应式与非 Web 应用不做运行栈阻断。
     */
    @Test
    void reactiveAndNonWebApplicationsShouldStart() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChaosDiagnosticsAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ChaosFeatureReporter.class));
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    private static Feature feature(ChaosFeatureReport report, String name) {
        return report.features().stream().filter(feature -> feature.name().equals(name)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> healthRuntime(ChaosRuntimeHealthIndicator indicator) {
        return (Map<String, Object>) indicator.health().getDetails().get("runtime");
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuleConfiguration {

        @Bean
        ChaosDiagnosticRule customRule() {
            return context -> List.of(new Finding(Severity.INFO, "custom", "custom-problem", "custom-fix"));
        }

        @Bean
        ChaosDiagnosticRule brokenRule() {
            return context -> {
                throw new IllegalStateException("broken");
            };
        }
    }

    /**
     * 配置声明的标识和贡献者 Bean 都要被自动装配接上，并渲染进启动日志。
     */
    @Test
    void shouldWireConfiguredAndContributedIdentifiers(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "chaos.diagnostics.startup-report.identifiers.[机房]=杭州-B",
                        "chaos.diagnostics.startup-report.identifiers.[发布批次]=2026-09-16.1")
                .withUserConfiguration(IdentifierContributorConfiguration.class)
                .run(context -> {
                    context.publishEvent(new ContextRefreshedEvent(context));

                    ChaosFeatureReport report = context.getBean(ChaosFeatureReporter.class).build();

                    assertThat(report.identifiers())
                            .containsEntry("机房", "杭州-B")
                            .containsEntry("发布批次", "2026-09-16.1")
                            .containsEntry("实例", "order-7d9f");
                    assertThat(output).contains("标识").contains("实例=order-7d9f");
                });
    }

    /**
     * 同名标识以配置为准：线上改标识不应该需要改代码重新发布。
     */
    @Test
    void configuredIdentifierShouldWinOverContributor() {
        contextRunner
                .withPropertyValues("chaos.diagnostics.startup-report.identifiers.[实例]=灰度-1")
                .withUserConfiguration(IdentifierContributorConfiguration.class)
                .run(context -> assertThat(context.getBean(ChaosFeatureReporter.class).build().identifiers())
                        .containsEntry("实例", "灰度-1"));
    }

    /**
     * 提供动态标识的贡献者 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    static class IdentifierContributorConfiguration {

        @Bean
        ChaosStartupIdentifierContributor instanceIdentifier() {
            return () -> java.util.Map.of("实例", "order-7d9f");
        }
    }

    /**
     * 故意把敏感配置写进诊断提示键名，验证输出前会被脱敏。
     */
    @Configuration(proxyBeanMethods = false)
    static class SecretSettingRuleConfiguration {

        @Bean
        ChaosDiagnosticRule secretEchoRule() {
            return context -> List.of(new Finding(Severity.INFO, "custom",
                    "client-secret=" + ChaosSettingMasker.mask("client-secret",
                            context.property("chaos.gateway.opaque-token.client-secret")),
                    "password=" + ChaosSettingMasker.mask("spring.datasource.password",
                            context.property("spring.datasource.password"))));
        }
    }
}
