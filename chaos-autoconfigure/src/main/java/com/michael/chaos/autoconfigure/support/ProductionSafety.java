package com.michael.chaos.autoconfigure.support;

import com.michael.chaos.core.constant.ProductionProfiles;
import com.michael.chaos.core.diagnostic.ChaosDiagnostic;
import com.michael.chaos.core.diagnostic.ChaosDiagnosticException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

/**
 * 生产环境安全检查：识别危险的 Noop/InMemory 兜底实现。
 *
 * <p>默认 fail-fast：生产模式下发现危险实现时抛出 {@link IllegalStateException} 阻断启动。
 * 原实现只输出 WARN 日志，生产环境遗漏配置时（例如没有 Redis 导致回退到本地限流、Noop JWT 撤销）
 * 服务仍会带着安全缺陷上线，与文档"启动时拒绝"的承诺不符。</p>
 *
 * <p>配置项（前缀 {@code chaos.production-safety}）：</p>
 * <ul>
 *     <li>{@code enabled}：总开关，默认 {@code true}。</li>
 *     <li>{@code production-mode}：显式声明是否为生产模式；未设置时按 profile 判断。</li>
 *     <li>{@code profiles}：视为生产的 profile 列表，默认 {@code prod,production,prd}。</li>
 *     <li>{@code fail-fast}：发现问题时是否阻断启动，默认 {@code true}；设为 {@code false} 时只告警。</li>
 *     <li>{@code allow-unsafe-defaults}：迁移期逃生开关，默认 {@code false}。</li>
 * </ul>
 */
public final class ProductionSafety {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionSafety.class);

    private static final String ENABLED_PROPERTY = "chaos.production-safety.enabled";
    private static final String PRODUCTION_MODE_PROPERTY = "chaos.production-safety.production-mode";
    private static final String ALLOW_UNSAFE_DEFAULTS_PROPERTY = "chaos.production-safety.allow-unsafe-defaults";
    private static final String FAIL_FAST_PROPERTY = "chaos.production-safety.fail-fast";
    private static final String PROFILES_PROPERTY = "chaos.production-safety.profiles";

    private ProductionSafety() {
    }

    /**
     * 判断当前环境是否需要执行生产安全检查。
     */
    public static boolean isProductionEnvironment(Environment environment) {
        return isEnabled(environment) && isProductionMode(environment) && !allowUnsafeDefaults(environment);
    }

    /**
     * 是否在发现问题时阻断启动。
     */
    public static boolean isFailFast(Environment environment) {
        return environment.getProperty(FAIL_FAST_PROPERTY, Boolean.class, true);
    }

    /**
     * 检查容器中是否存在危险默认实现，按 fail-fast 配置阻断启动或输出告警。
     *
     * @param environment Spring 环境
     * @param beanFactory Bean 工厂
     * @param unsafeBeanClasses 危险实现类全限定名到提示信息的映射
     */
    public static void checkUnsafeDefaultBeans(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            Map<String, String> unsafeBeanClasses) {
        if (!isProductionEnvironment(environment)) {
            return;
        }
        List<String> violations = unsafeBeanClasses.entrySet().stream()
                .map(entry -> describeViolation(beanFactory, entry.getKey(), entry.getValue()))
                .filter(violation -> violation != null)
                .toList();
        report(environment, violations);
    }

    /**
     * 历史方法名，行为与 {@link #checkUnsafeDefaultBeans} 一致（默认 fail-fast）。
     *
     * <p>保留该方法是为了让已有 autoconfigure 模块无需修改即可获得阻断能力；
     * 新代码请使用 {@link #checkUnsafeDefaultBeans}，只需告警时配置 {@code chaos.production-safety.fail-fast=false}。</p>
     */
    public static void warnUnsafeDefaultBeans(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            Map<String, String> unsafeBeanClasses) {
        checkUnsafeDefaultBeans(environment, beanFactory, unsafeBeanClasses);
    }

    /**
     * 与 {@link #checkUnsafeDefaultBeans} 一致。
     */
    public static void rejectUnsafeDefaultBeans(
            Environment environment,
            ConfigurableListableBeanFactory beanFactory,
            Map<String, String> unsafeBeanClasses) {
        checkUnsafeDefaultBeans(environment, beanFactory, unsafeBeanClasses);
    }

    /**
     * 统一输出检查结果。
     *
     * @param environment Spring 环境
     * @param violations 违规描述
     * @throws IllegalStateException fail-fast 模式下存在违规时抛出
     */
    public static void report(Environment environment, List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            return;
        }
        ChaosDiagnostic diagnostic = diagnostic(violations);
        if (isFailFast(environment)) {
            throw new ChaosDiagnosticException(diagnostic);
        }
        LOGGER.warn(diagnostic.format());
    }

    /**
     * 构造生产安全检查的可操作诊断信息。
     *
     * @param violations 每一项违规描述（应包含替换建议和 Bean 名称）
     */
    public static ChaosDiagnostic diagnostic(List<String> violations) {
        return new ChaosDiagnostic(
                "生产安全检查未通过，容器中存在 " + violations.size() + " 个只适用于开发环境的实现",
                violations,
                List.of(
                        "按上面每一项替换为生产实现，常见做法是引入 chaos-redis-starter 并配置 spring.data.redis.*，"
                                + "Redis 版限流、幂等、JWT 撤销实现会自动替换内存/Noop 兜底实现",
                        "确认当前确实是生产环境：生产 profile 由 chaos.production-safety.profiles 决定（默认 prod,production,prd），"
                                + "也可以用 chaos.production-safety.production-mode 显式声明",
                        "迁移期确需临时放行：chaos.production-safety.fail-fast=false（只告警）"
                                + "或 chaos.production-safety.allow-unsafe-defaults=true（跳过检查）"));
    }

    /**
     * 为容器中存在的危险实现生成带 Bean 名称的违规描述；不存在时返回 {@code null}。
     */
    static String describeViolation(ConfigurableListableBeanFactory beanFactory, String className, String message) {
        Class<?> type = resolveClass(beanFactory, className);
        if (type == null) {
            return null;
        }
        String[] beanNames = beanFactory.getBeanNamesForType(type, true, false);
        return beanNames.length == 0 ? null : withBeanNames(message, beanNames);
    }

    /**
     * 在违规描述后追加 Bean 名称，便于定位是哪个配置注册了危险实现。
     */
    public static String withBeanNames(String message, String[] beanNames) {
        return message + "（Bean：" + String.join(", ", beanNames) + "）";
    }

    /**
     * 当前环境是否被识别为生产模式（不考虑总开关与逃生开关），供启动报告展示。
     */
    public static boolean isProductionMode(Environment environment) {
        Boolean explicitMode = environment.getProperty(PRODUCTION_MODE_PROPERTY, Boolean.class);
        return explicitMode == null ? isProductionProfile(environment) : explicitMode;
    }

    /**
     * 是否开启了迁移期逃生开关 {@code chaos.production-safety.allow-unsafe-defaults}。
     */
    public static boolean isUnsafeDefaultsAllowed(Environment environment) {
        return allowUnsafeDefaults(environment);
    }

    private static boolean isEnabled(Environment environment) {
        return environment.getProperty(ENABLED_PROPERTY, Boolean.class, true);
    }

    private static boolean allowUnsafeDefaults(Environment environment) {
        return environment.getProperty(ALLOW_UNSAFE_DEFAULTS_PROPERTY, Boolean.class, false);
    }

    private static boolean isProductionProfile(Environment environment) {
        String[] productionProfiles = environment.getProperty(
                PROFILES_PROPERTY, String[].class, ProductionProfiles.defaultsArray());
        List<String> normalizedProfiles = Arrays.stream(productionProfiles)
                .filter(profile -> profile != null && !profile.isBlank())
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (normalizedProfiles.isEmpty()) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> profile != null && !profile.isBlank())
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedProfiles::contains);
    }

    private static Class<?> resolveClass(ConfigurableListableBeanFactory beanFactory, String className) {
        try {
            ClassLoader classLoader = beanFactory.getBeanClassLoader();
            return Class.forName(className, false, classLoader == null ? ProductionSafety.class.getClassLoader() : classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
