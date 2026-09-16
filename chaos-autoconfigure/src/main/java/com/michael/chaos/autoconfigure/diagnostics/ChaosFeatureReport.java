package com.michael.chaos.autoconfigure.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chaos 启动报告：当前应用启用了哪些框架能力、为什么没启用、关键配置和诊断提示。
 *
 * <p>启动日志与 {@code /actuator/chaos} 端点共用这一份模型，保证两处看到的内容一致。
 * 模型只包含展示用字符串，所有配置值在构建时已完成脱敏。</p>
 *
 * @param application 应用名（{@code spring.application.name}）
 * @param activeProfiles 激活的 profile
 * @param productionMode 是否被识别为生产模式
 * @param failFast 生产安全检查是否 fail-fast
 * @param features 各功能状态
 * @param findings 诊断提示
 */
public record ChaosFeatureReport(
        String application,
        List<String> activeProfiles,
        boolean productionMode,
        boolean failFast,
        List<Feature> features,
        List<Finding> findings) {

    /**
     * 规范化集合参数。
     */
    public ChaosFeatureReport {
        activeProfiles = List.copyOf(activeProfiles);
        features = List.copyOf(features);
        findings = List.copyOf(findings);
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
}
