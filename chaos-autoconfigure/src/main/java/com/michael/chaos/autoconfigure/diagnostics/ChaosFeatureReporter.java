package com.michael.chaos.autoconfigure.diagnostics;

import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Feature;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.michael.chaos.autoconfigure.support.ProductionSafety;
import java.util.ArrayList;
import java.util.Collections;
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

    private final Map<String, String> configuredIdentifiers;

    private final List<ChaosStartupIdentifierContributor> identifierContributors;

    /**
     * 创建报告构建器，不带自定义启动标识。
     */
    public ChaosFeatureReporter(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            List<ChaosDiagnosticRule> rules) {
        this(beanFactory, environment, rules, Map.of(), List.of());
    }

    /**
     * 创建报告构建器。
     *
     * @param beanFactory Bean 工厂
     * @param environment Spring 环境
     * @param rules 诊断规则（内置规则 + 业务自定义规则）
     * @param configuredIdentifiers 配置中声明的静态启动标识
     * @param identifierContributors 运行期动态标识贡献者
     */
    public ChaosFeatureReporter(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            List<ChaosDiagnosticRule> rules,
            Map<String, String> configuredIdentifiers,
            List<ChaosStartupIdentifierContributor> identifierContributors) {
        this.beanFactory = beanFactory;
        this.environment = environment;
        this.rules = List.copyOf(rules);
        // 不用 Map.copyOf：YAML 里写 "机房:" 不给值会绑定成 null value，copyOf 会直接 NPE 让应用起不来。
        this.configuredIdentifiers = configuredIdentifiers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuredIdentifiers));
        this.identifierContributors = identifierContributors == null ? List.of() : List.copyOf(identifierContributors);
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
                identifiers(),
                features,
                findings);
    }

    /**
     * 合并启动标识：先按 Bean 顺序收集贡献者，再用配置覆盖同名 key。
     *
     * <p>配置优先是刻意的——线上临时要改某个标识（比如把机房标成"灰度"）应该能靠配置完成，不必改代码重新发布。</p>
     */
    private Map<String, String> identifiers() {
        Map<String, String> merged = new LinkedHashMap<>();
        for (ChaosStartupIdentifierContributor contributor : identifierContributors) {
            try {
                Map<String, String> contributed = contributor.identifiers();
                if (contributed != null) {
                    contributed.forEach((key, value) -> put(merged, key, value));
                }
            } catch (RuntimeException ex) {
                // 标识只是辅助信息，贡献者异常不能影响启动或端点可用性。
                LOGGER.debug("Chaos startup identifier contributor {} failed: {}",
                        contributor.getClass().getName(), ex.getMessage(), ex);
            }
        }
        configuredIdentifiers.forEach((key, value) -> put(merged, key, value));
        return merged;
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        target.put(key, ChaosSettingMasker.mask(key, value == null ? "" : value));
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
