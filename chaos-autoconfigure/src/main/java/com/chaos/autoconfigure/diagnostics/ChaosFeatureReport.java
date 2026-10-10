package com.chaos.autoconfigure.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chaos 诊断报告：当前应用启用了哪些框架能力、为什么没启用、关键配置和诊断提示。
 *
 * <p>启动日志与 {@code chaosRuntime} 健康组件共用这一份模型，避免不同诊断入口的数据不一致。
 * 模型只包含展示用字符串，所有配置值在构建时已完成脱敏。</p>
 *
 * @param application 应用名（{@code spring.application.name}）
 * @param activeProfiles 激活的 profile
 * @param productionMode 是否被识别为生产模式
 * @param failFast 生产安全检查是否 fail-fast
 * @param identifiers 自定义启动标识（已脱敏），来自配置与 {@code ChaosStartupIdentifierContributor}
 * @param features 各功能状态
 * @param findings 诊断提示
 * @param runtime 运行环境、访问地址与配置明细（已脱敏）
 */
public record ChaosFeatureReport(
        String application,
        List<String> activeProfiles,
        boolean productionMode,
        boolean failFast,
        Map<String, String> identifiers,
        List<Feature> features,
        List<Finding> findings,
        RuntimeDetails runtime) {

    /**
     * 规范化集合参数。
     */
    public ChaosFeatureReport {
        activeProfiles = List.copyOf(activeProfiles);
        identifiers = identifiers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(identifiers));
        features = List.copyOf(features);
        findings = List.copyOf(findings);
        runtime = runtime == null ? RuntimeDetails.empty() : runtime;
    }

    /**
     * 创建不带运行详情的报告，兼容既有调用方。
     */
    public ChaosFeatureReport(
            String application,
            List<String> activeProfiles,
            boolean productionMode,
            boolean failFast,
            Map<String, String> identifiers,
            List<Feature> features,
            List<Finding> findings) {
        this(application, activeProfiles, productionMode, failFast, identifiers, features, findings, RuntimeDetails.empty());
    }

    /**
     * 返回已启用的功能。
     */
    public List<Feature> enabledFeatures() {
        return features.stream().filter(Feature::enabled).toList();
    }

    /**
     * 单个功能的状态。
     *
     * @param name 功能名，与 starter 名称对应（例如 {@code web}、{@code gateway}）
     * @param enabled 是否启用
     * @param category 未启用的原因分类；启用时为 {@link DisabledCategory#NONE}
     * @param reason 未启用的具体原因（来自 Spring 条件评估报告）；启用时为空字符串
     * @param settings 关键生效配置（已脱敏）
     */
    public record Feature(
            String name,
            boolean enabled,
            DisabledCategory category,
            String reason,
            Map<String, String> settings) {

        /**
         * 规范化参数。
         */
        public Feature {
            reason = reason == null ? "" : reason;
            settings = settings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }
    }

    /**
     * 功能未启用的原因分类。
     */
    public enum DisabledCategory {
        /**
         * 已启用。
         */
        NONE,
        /**
         * 缺少对应 starter 或三方依赖。
         */
        MISSING_DEPENDENCY,
        /**
         * 应用类型不匹配（例如网关只在响应式应用中生效）。
         */
        WEB_APPLICATION_TYPE,
        /**
         * 被配置项关闭。
         */
        DISABLED_BY_PROPERTY,
        /**
         * 被 {@code spring.autoconfigure.exclude} 排除。
         */
        EXCLUDED,
        /**
         * 自动装配没有被导入。
         */
        NOT_IMPORTED,
        /**
         * 其他条件不满足。
         */
        OTHER
    }

    /**
     * 诊断提示。
     *
     * @param severity 严重程度
     * @param feature 相关功能名
     * @param problem 问题描述
     * @param fix 修复建议
     */
    public record Finding(Severity severity, String feature, String problem, String fix) {
    }

    /**
     * 诊断提示严重程度。
     */
    public enum Severity {
        /**
         * 提示：当前可用，但上线前需要注意。
         */
        INFO,
        /**
         * 告警：很可能是配置遗漏，建议尽快处理。
         */
        WARN,
        /**
         * 错误：功能无法按预期工作。
         */
        ERROR
    }

    /**
     * 应用运行环境、完整访问地址与最终生效配置。
     *
     * @param webApplicationType Web 应用类型
     * @param bindAddress 服务绑定地址
     * @param applicationPort 应用实际端口；非 Web 应用为空
     * @param managementPort 管理端点实际端口；未启用管理 Web 端点时为空
     * @param endpoints 常用入口及其当前状态
     * @param configurationSources 参与配置加载的 YAML 来源
     * @param effectiveYamlConfiguration YAML 声明键对应的最终生效值
     * @param systemEnvironment 系统环境变量
     * @param jvmSystemProperties JVM 系统属性
     */
    public record RuntimeDetails(
            String webApplicationType,
            String bindAddress,
            Integer applicationPort,
            Integer managementPort,
            List<EndpointUrl> endpoints,
            List<String> configurationSources,
            Map<String, String> effectiveYamlConfiguration,
            Map<String, String> systemEnvironment,
            Map<String, String> jvmSystemProperties) {

        /**
         * 规范化集合参数。
         */
        public RuntimeDetails {
            webApplicationType = webApplicationType == null ? "UNKNOWN" : webApplicationType;
            bindAddress = bindAddress == null ? "" : bindAddress;
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
            configurationSources = configurationSources == null ? List.of() : List.copyOf(configurationSources);
            effectiveYamlConfiguration = immutableMap(effectiveYamlConfiguration);
            systemEnvironment = immutableMap(systemEnvironment);
            jvmSystemProperties = immutableMap(jvmSystemProperties);
        }

        /**
         * 返回不包含运行信息的空对象。
         */
        public static RuntimeDetails empty() {
            return new RuntimeDetails("UNKNOWN", "", null, null, List.of(), List.of(), Map.of(), Map.of(), Map.of());
        }

        private static Map<String, String> immutableMap(Map<String, String> source) {
            return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }

    /**
     * 一个可访问入口及其配置状态。
     *
     * @param name 入口名称
     * @param url 完整 URL
     * @param status 当前状态
     */
    public record EndpointUrl(String name, String url, EndpointStatus status) {
    }

    /**
     * 入口配置状态。
     */
    public enum EndpointStatus {
        /**
         * 普通应用入口已启用。
         */
        ENABLED,
        /**
         * Actuator 端点已通过 Web 暴露。
         */
        EXPOSED,
        /**
         * Actuator 端点存在但未通过 Web 暴露。
         */
        NOT_EXPOSED,
        /**
         * 入口被配置关闭。
         */
        DISABLED
    }
}
