package com.michael.chaos.autoconfigure.diagnostics;

import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.michael.chaos.autoconfigure.support.ProductionSafety;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcomes;
import org.springframework.core.env.Environment;

/**
 * 构建 {@link ChaosFeatureReport}。
 *
 * <p>功能是否启用以"自动装配类是否注册为 Bean 定义"为准；未启用的原因取自 Spring Boot 的
 * {@link ConditionEvaluationReport}（与 {@code --debug} 输出同源），避免自己重复实现条件判断而产生偏差。</p>
 */
public class ChaosFeatureReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChaosFeatureReporter.class);

    private final ConfigurableListableBeanFactory beanFactory;

    private final Environment environment;

    private final List<ChaosDiagnosticRule> rules;

    /**
     * 创建报告构建器。
     *
     * @param beanFactory Bean 工厂
     * @param environment Spring 环境
     * @param rules 诊断规则（内置规则 + 业务自定义规则）
     */
    public ChaosFeatureReporter(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            List<ChaosDiagnosticRule> rules) {
        this.beanFactory = beanFactory;
        this.environment = environment;
        this.rules = List.copyOf(rules);
    }

    /**
     * 按当前容器状态构建报告。每次调用都会重新计算，actuator 端点可以看到运行期的最新 Bean 状态。
     */
    public ChaosFeatureReport build() {
        ConditionEvaluationReport conditionReport = ConditionEvaluationReport.get(beanFactory);
        Map<String, ConditionAndOutcomes> outcomes = conditionReport.getConditionAndOutcomesBySource();
        Set<String> exclusions = new LinkedHashSet<>(conditionReport.getExclusions());
        boolean productionMode = ProductionSafety.isProductionMode(environment);

        Set<String> enabled = new LinkedHashSet<>();
        for (ChaosFeatureCatalog.Entry entry : ChaosFeatureCatalog.entries()) {
            if (beanFactory.containsBeanDefinition(entry.autoConfigurationClass())) {
                enabled.add(entry.feature());
            }
        }
        ChaosDiagnosticContext context = new ChaosDiagnosticContext(environment, beanFactory, productionMode, enabled);

        List<Feature> features = new ArrayList<>();
        for (ChaosFeatureCatalog.Entry entry : ChaosFeatureCatalog.entries()) {
            if (enabled.contains(entry.feature())) {
                features.add(new Feature(entry.feature(), true, DisabledCategory.NONE, "", settings(entry, context)));
            } else {
                features.add(disabledFeature(entry, outcomes.get(entry.autoConfigurationClass()), exclusions));
            }
        }

        List<Finding> findings = new ArrayList<>();
        for (ChaosDiagnosticRule rule : rules) {
            try {
                List<Finding> result = rule.evaluate(context);
                if (result != null) {
                    findings.addAll(result);
                }
            } catch (RuntimeException ex) {
                // 诊断只是辅助信息，任何规则异常都不能影响启动或端点可用性。
                LOGGER.debug("Chaos diagnostic rule {} failed: {}", rule.getClass().getName(), ex.getMessage(), ex);
            }
        }
        findings.sort((left, right) -> right.severity().compareTo(left.severity()));

        return new ChaosFeatureReport(
                environment.getProperty("spring.application.name", "application"),
                List.of(environment.getActiveProfiles()),
                productionMode,
                ProductionSafety.isFailFast(environment),
                features,
                findings);
    }

    private static Map<String, String> settings(ChaosFeatureCatalog.Entry entry, ChaosDiagnosticContext context) {
        try {
            Map<String, String> masked = new LinkedHashMap<>();
            entry.settings().apply(context).forEach((key, value) -> masked.put(key, ChaosSettingMasker.mask(key, value)));
            return masked;
        } catch (RuntimeException | LinkageError ex) {
            LOGGER.debug("Failed to collect settings for chaos feature {}: {}", entry.feature(), ex.getMessage(), ex);
            return Map.of();
        }
    }

    private static Feature disabledFeature(
            ChaosFeatureCatalog.Entry entry,
            ConditionAndOutcomes conditionAndOutcomes,
            Set<String> exclusions) {
        if (exclusions.contains(entry.autoConfigurationClass())) {
            return new Feature(entry.feature(), false, DisabledCategory.EXCLUDED, "被 spring.autoconfigure.exclude 排除", Map.of());
        }
        if (conditionAndOutcomes == null) {
            return new Feature(entry.feature(), false, DisabledCategory.NOT_IMPORTED, "自动装配未被导入", Map.of());
        }
        for (ConditionAndOutcome conditionAndOutcome : conditionAndOutcomes) {
            if (!conditionAndOutcome.getOutcome().isMatch()) {
                String message = conditionAndOutcome.getOutcome().getMessage();
                return new Feature(entry.feature(), false, categorize(message), message, Map.of());
            }
        }
        return new Feature(entry.feature(), false, DisabledCategory.OTHER, "条件不满足", Map.of());
    }

    /**
     * 按 Spring Boot 条件消息归类未启用原因。
     */
    static DisabledCategory categorize(String message) {
        if (message == null) {
            return DisabledCategory.OTHER;
        }
        if (message.contains("did not find required class")) {
            return DisabledCategory.MISSING_DEPENDENCY;
        }
        if (message.contains("web application") || message.contains("@ConditionalOnWebApplication")) {
            return DisabledCategory.WEB_APPLICATION_TYPE;
        }
        if (message.contains("@ConditionalOnProperty")) {
            return DisabledCategory.DISABLED_BY_PROPERTY;
        }
        return DisabledCategory.OTHER;
    }
}
