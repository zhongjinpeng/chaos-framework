package com.chaos.autoconfigure.diagnostics;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.RuntimeDetails;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;

/**
 * 在 Actuator 健康检查中提供 Chaos 运行环境与配置诊断信息。
 *
 * <p>该组件只展示运行信息，不执行外部依赖探测，因此状态始终为 {@code UP}。配置值来自
 * {@link ChaosFeatureReporter} 构建的统一报告，进入健康详情前已经完成敏感键、连接串凭据和
 * JVM 参数脱敏，避免启动日志与健康端点使用两套不一致的数据来源。</p>
 *
 * <p>组件 ID 为 {@code chaosRuntime}，所有诊断数据统一出现在 {@code /actuator/health} 的
 * {@code components.chaosRuntime.details} 中。是否向 HTTP 调用方展示组件和详情仍由 Spring Boot 的
 * {@code management.endpoint.health.show-components} 与 {@code show-details} 安全策略决定。</p>
 */
public final class ChaosRuntimeHealthIndicator implements HealthIndicator {

    private final ChaosFeatureReporter reporter;

    private final ApplicationContext applicationContext;

    private final BuildProperties buildProperties;

    private final Clock clock;

    /**
     * 创建运行环境健康信息提供者。
     *
     * @param reporter Chaos 统一报告构建器
     */
    public ChaosRuntimeHealthIndicator(ChaosFeatureReporter reporter) {
        this(reporter, null, null, Clock.systemUTC());
    }

    ChaosRuntimeHealthIndicator(
            ChaosFeatureReporter reporter,
            ApplicationContext applicationContext,
            BuildProperties buildProperties,
            Clock clock) {
        this.reporter = reporter;
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
        this.clock = clock;
    }

    /**
     * 返回应用身份、运行地址以及已脱敏的配置明细。
     */
    @Override
    public Health health() {
        ChaosFeatureReport report = reporter.build();
        return Health.up().withDetails(details(report)).build();
    }

    private Map<String, Object> details(ChaosFeatureReport report) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("application", report.application());
        details.put("serviceVersion", serviceVersion());
        if (buildProperties != null && buildProperties.getTime() != null) {
            details.put("buildTime", DateTimeFormatter.ISO_INSTANT.format(buildProperties.getTime()));
        }
        if (applicationContext != null) {
            Instant startedAt = Instant.ofEpochMilli(applicationContext.getStartupDate());
            details.put("startedAt", DateTimeFormatter.ISO_INSTANT.format(startedAt));
            details.put("uptimeSeconds", Math.max(0L, Duration.between(startedAt, clock.instant()).toSeconds()));
        }
        details.put("activeProfiles", report.activeProfiles());
        details.put("productionMode", report.productionMode());
        details.put("failFast", report.failFast());
        putIfNotEmpty(details, "identifiers", report.identifiers());
        details.put("features", enabledFeatures(report.enabledFeatures()));
        putIfNotEmpty(details, "findings", report.findings());
        details.put("runtime", runtime(report.runtime()));
        return details;
    }

    private String serviceVersion() {
        if (buildProperties != null && hasText(buildProperties.getVersion())) {
            return buildProperties.getVersion();
        }
        if (applicationContext != null) {
            String configured = applicationContext.getEnvironment().getProperty("spring.application.version");
            if (hasText(configured)) {
                return configured.strip();
            }
        }
        return "unknown";
    }

    private static List<Map<String, Object>> enabledFeatures(List<Feature> features) {
        List<Map<String, Object>> result = new ArrayList<>(features.size());
        for (Feature feature : features) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", feature.name());
            putIfNotEmpty(item, "settings", feature.settings());
            result.add(item);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> runtime(RuntimeDetails runtime) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("webApplicationType", runtime.webApplicationType());
        putIfHasText(details, "bindAddress", runtime.bindAddress());
        putIfNotNull(details, "applicationPort", runtime.applicationPort());
        putIfNotNull(details, "managementPort", runtime.managementPort());
        putIfNotEmpty(details, "endpoints", runtime.endpoints());
        putIfNotEmpty(details, "configurationSources", runtime.configurationSources());
        putIfNotEmpty(details, "effectiveYamlConfiguration", runtime.effectiveYamlConfiguration());
        putIfNotEmpty(details, "systemEnvironment", runtime.systemEnvironment());
        putIfNotEmpty(details, "jvmSystemProperties", runtime.jvmSystemProperties());
        return details;
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, Map<?, ?> value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, List<?> value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIfHasText(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
