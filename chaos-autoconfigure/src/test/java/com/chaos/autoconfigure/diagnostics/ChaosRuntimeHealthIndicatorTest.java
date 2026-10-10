package com.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.RuntimeDetails;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Severity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;

/**
 * Chaos 运行环境健康组件测试。
 */
class ChaosRuntimeHealthIndicatorTest {

    /**
     * 健康详情复用统一报告中的脱敏数据，不重新读取或还原原始配置值。
     */
    @Test
    void shouldExposeSanitizedRuntimeDetails() {
        RuntimeDetails runtime = new RuntimeDetails(
                "SERVLET",
                "0.0.0.0",
                8080,
                8081,
                List.of(),
                List.of("application.yml"),
                Map.of("spring.datasource.password", ChaosSettingMasker.MASK),
                Map.of("API_TOKEN", ChaosSettingMasker.MASK),
                Map.of("service.secret", ChaosSettingMasker.MASK));
        Map<String, String> identifiers = Map.of("instance", "order-1");
        List<Feature> features = List.of(
                new Feature("web", true, DisabledCategory.NONE, "", Map.of("rate-limiter", "redis")));
        List<Finding> findings = List.of(
                new Finding(Severity.WARN, "redis", "key prefix is missing", "configure key-prefix"));
        ChaosFeatureReport report = new ChaosFeatureReport(
                "order-service", List.of("dev"), false, true, identifiers, features, findings, runtime);
        ChaosFeatureReporter reporter = mock(ChaosFeatureReporter.class);
        when(reporter.build()).thenReturn(report);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getStartupDate()).thenReturn(Instant.parse("2026-10-10T07:00:00Z").toEpochMilli());
        Properties buildEntries = new Properties();
        buildEntries.setProperty("version", "1.4.2");
        buildEntries.setProperty("time", "2026-10-10T06:55:00Z");

        Health health = new ChaosRuntimeHealthIndicator(
                        reporter,
                        applicationContext,
                        new BuildProperties(buildEntries),
                        Clock.fixed(Instant.parse("2026-10-10T07:01:30Z"), ZoneOffset.UTC))
                .health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("application", "order-service")
                .containsEntry("serviceVersion", "1.4.2")
                .containsEntry("buildTime", "2026-10-10T06:55:00Z")
                .containsEntry("startedAt", "2026-10-10T07:00:00Z")
                .containsEntry("uptimeSeconds", 90L)
                .containsEntry("activeProfiles", List.of("dev"))
                .containsEntry("productionMode", false)
                .containsEntry("failFast", true)
                .containsEntry("identifiers", identifiers)
                .containsEntry("findings", findings);
        assertThat(health.getDetails().get("features"))
                .isEqualTo(List.of(Map.of("name", "web", "settings", Map.of("rate-limiter", "redis"))));
        assertThat(runtimeDetails(health))
                .containsEntry("webApplicationType", "SERVLET")
                .containsEntry("applicationPort", 8080)
                .containsEntry("systemEnvironment", Map.of("API_TOKEN", ChaosSettingMasker.MASK));
        assertThat(health.toString()).doesNotContain("database-secret", "environment-secret", "jvm-secret");
    }

    /**
     * 空标识、空诊断和未启用功能不应占用健康响应。
     */
    @Test
    void shouldOmitEmptySectionsAndDisabledFeatures() {
        ChaosFeatureReport report = new ChaosFeatureReport(
                "order-service",
                List.of("prod"),
                true,
                true,
                Map.of(),
                List.of(new Feature("job", false, DisabledCategory.MISSING_DEPENDENCY, "missing", Map.of())),
                List.of(),
                RuntimeDetails.empty());
        ChaosFeatureReporter reporter = mock(ChaosFeatureReporter.class);
        when(reporter.build()).thenReturn(report);

        Health health = new ChaosRuntimeHealthIndicator(reporter).health();

        assertThat(health.getDetails())
                .containsEntry("serviceVersion", "unknown")
                .doesNotContainKeys("identifiers", "findings");
        assertThat(health.getDetails().get("features")).isEqualTo(List.of());
        assertThat(runtimeDetails(health)).containsOnly(Map.entry("webApplicationType", "UNKNOWN"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runtimeDetails(Health health) {
        return (Map<String, Object>) health.getDetails().get("runtime");
    }
}
