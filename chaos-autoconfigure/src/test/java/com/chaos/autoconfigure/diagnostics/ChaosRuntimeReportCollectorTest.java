package com.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointStatus;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointUrl;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.RuntimeDetails;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 运行环境报告采集测试。
 */
class ChaosRuntimeReportCollectorTest {

    /**
     * 独立管理端口、context path、端点映射和 SpringDoc 自定义路径会组合成完整 URL。
     */
    @Test
    void shouldBuildCompleteEndpointUrlsAndExposureStatus() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("server.address", "0.0.0.0")
                .withProperty("local.server.port", "18080")
                .withProperty("server.servlet.context-path", "/api")
                .withProperty("management.server.port", "19090")
                .withProperty("local.management.port", "19091")
                .withProperty("management.server.address", "127.0.0.1")
                .withProperty("management.server.base-path", "/manage")
                .withProperty("management.endpoints.web.base-path", "/ops")
                .withProperty("management.endpoints.web.path-mapping.health", "ready")
                .withProperty("management.endpoints.web.exposure.include", "health")
                .withProperty("springdoc.api-docs.path", "/openapi")
                .withProperty("springdoc.swagger-ui.path", "/docs");

        RuntimeDetails details = collector(environment, true).collect();

        assertThat(details.webApplicationType()).isEqualTo("SERVLET");
        assertThat(details.applicationPort()).isEqualTo(18080);
        assertThat(details.managementPort()).isEqualTo(19091);
        assertThat(endpoint(details, "application"))
                .isEqualTo(new EndpointUrl("application", "http://localhost:18080/api", EndpointStatus.ENABLED));
        assertThat(endpoint(details, "actuator"))
                .isEqualTo(new EndpointUrl("actuator", "http://127.0.0.1:19091/manage/ops", EndpointStatus.EXPOSED));
        assertThat(endpoint(details, "health"))
                .isEqualTo(new EndpointUrl("health", "http://127.0.0.1:19091/manage/ops/ready", EndpointStatus.EXPOSED));
        assertThat(endpoint(details, "openapi").url()).isEqualTo("http://localhost:18080/api/openapi");
        assertThat(endpoint(details, "swagger-ui").url()).isEqualTo("http://localhost:18080/api/docs");
    }

    /**
     * YAML 区块展示环境覆盖后的最终值；环境变量、JVM 属性和连接串统一脱敏并排序。
     */
    @Test
    void shouldCollectEffectiveConfigurationAndMaskEverySource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.main.web-application-type", "servlet");
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.yml]' via location 'classpath:/'",
                Map.of(
                        "app.timeout", "30",
                        "spring.datasource.password", "database-secret",
                        "spring.datasource.url", "jdbc:mysql://db-user:db-pass@db:3306/app?password=query-secret")));
        environment.getPropertySources().addFirst(new MapPropertySource(
                "commandLineArgs", Map.of("app.timeout", "45")));

        RuntimeDetails details = new ChaosRuntimeReportCollector(
                environment,
                null,
                true,
                Map.of(
                        "CHAOS_API_TOKEN", "environment-secret",
                        "HOME", "/home/runtime-user",
                        "JAVA_TOOL_OPTIONS", "-Dservice.password=tool-secret -Xmx512m"),
                Map.of(
                        "chaos.client.secret", "jvm-secret",
                        "java.class.path", "/sensitive/application/classpath",
                        "spring.service.url", "https://user:pass@example.com/config?token=url-secret",
                        "line.separator", "\n"))
                .collect();

        assertThat(details.configurationSources()).singleElement().asString().contains("application.yml");
        assertThat(details.effectiveYamlConfiguration())
                .containsEntry("app.timeout", "45")
                .containsEntry("spring.datasource.password", ChaosSettingMasker.MASK)
                .containsEntry("spring.datasource.url", "jdbc:mysql://db:3306/app");
        assertThat(details.systemEnvironment())
                .containsEntry("CHAOS_API_TOKEN", ChaosSettingMasker.MASK)
                .containsEntry("JAVA_TOOL_OPTIONS", "-Dservice.password=****** -Xmx512m")
                .doesNotContainKey("HOME");
        assertThat(details.jvmSystemProperties())
                .containsEntry("chaos.client.secret", ChaosSettingMasker.MASK)
                .containsEntry("spring.service.url", "https://example.com/config")
                .doesNotContainKeys("java.class.path", "line.separator");
        assertThat(details.jvmSystemProperties().keySet())
                .containsExactly("chaos.client.secret", "spring.service.url");

        String rendered = ChaosStartupReportRenderer.render(report(details));
        assertThat(rendered)
                .doesNotContain("YAML 配置来源", "YAML 最终生效配置", "系统环境变量", "JVM 系统属性")
                .doesNotContain(
                        "database-secret", "db-user", "db-pass", "query-secret",
                        "environment-secret", "tool-secret", "jvm-secret", "url-secret");
    }

    /**
     * 关闭详细配置只移除三个配置区块，不影响运行信息和端点 URL。
     */
    @Test
    void detailsSwitchShouldNotHideRuntimeAndEndpointUrls() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("server.port", "8088")
                .withProperty("springdoc.api-docs.enabled", "true");
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.yml]'", Map.of("app.name", "demo")));

        RuntimeDetails details = collector(environment, false).collect();

        assertThat(details.endpoints()).isNotEmpty();
        assertThat(endpoint(details, "application").url()).isEqualTo("http://localhost:8088/");
        assertThat(details.configurationSources()).isEmpty();
        assertThat(details.effectiveYamlConfiguration()).isEmpty();
        assertThat(details.systemEnvironment()).isEmpty();
        assertThat(details.jvmSystemProperties()).isEmpty();
    }

    private static ChaosRuntimeReportCollector collector(MockEnvironment environment, boolean includeDetails) {
        return new ChaosRuntimeReportCollector(
                environment, null, includeDetails, Map.of("REGION", "cn-hangzhou"), Map.of("java.version", "21"));
    }

    private static EndpointUrl endpoint(RuntimeDetails details, String name) {
        return details.endpoints().stream()
                .filter(endpoint -> endpoint.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ChaosFeatureReport report(RuntimeDetails details) {
        return new ChaosFeatureReport("demo", java.util.List.of("dev"), false, true,
                Map.of(), java.util.List.of(), java.util.List.of(), details);
    }
}
