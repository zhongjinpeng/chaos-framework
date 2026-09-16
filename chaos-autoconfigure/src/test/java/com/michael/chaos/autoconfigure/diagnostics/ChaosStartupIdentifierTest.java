package com.michael.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * 自定义启动标识测试。
 */
class ChaosStartupIdentifierTest {

    private final MockEnvironment environment = new MockEnvironment();

    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

    private ChaosFeatureReport build(Map<String, String> configured, List<ChaosStartupIdentifierContributor> contributors) {
        return new ChaosFeatureReporter(beanFactory, environment, List.of(), configured, contributors).build();
    }

    /**
     * 没有配置标识时报告头保持原样，不多出空行。
     */
    @Test
    void shouldRenderNothingWhenNoIdentifier() {
        String rendered = ChaosStartupReportRenderer.render(build(Map.of(), List.of()));

        assertThat(rendered).doesNotContain("标识");
    }

    /**
     * 配置的静态标识按声明顺序渲染。
     */
    @Test
    void shouldRenderConfiguredIdentifiersInOrder() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("版本", "2.3.1");
        configured.put("机房", "杭州-B");

        String rendered = ChaosStartupReportRenderer.render(build(configured, List.of()));

        assertThat(rendered).contains("标识  版本=2.3.1 | 机房=杭州-B");
    }

    /**
     * 贡献者提供动态标识；多个贡献者按顺序合并。
     */
    @Test
    void shouldMergeContributors() {
        ChaosFeatureReport report = build(Map.of(), List.of(
                () -> Map.of("实例", "order-7d9f"),
                () -> Map.of("可用区", "cn-hangzhou-b")));

        assertThat(report.identifiers()).containsEntry("实例", "order-7d9f").containsEntry("可用区", "cn-hangzhou-b");
    }

    /**
     * 同名 key 以配置为准：线上临时改标识不应该需要改代码重新发布。
     */
    @Test
    void configuredIdentifierShouldOverrideContributor() {
        ChaosFeatureReport report = build(Map.of("机房", "灰度"), List.of(() -> Map.of("机房", "杭州-B")));

        assertThat(report.identifiers()).containsEntry("机房", "灰度");
    }

    /**
     * 贡献者抛异常不能影响启动或端点，其余标识照常输出。
     */
    @Test
    void shouldIgnoreFailingContributor() {
        ChaosFeatureReport report = build(Map.of(), List.of(
                () -> {
                    throw new IllegalStateException("hostname lookup failed");
                },
                () -> Map.of("实例", "order-7d9f")));

        assertThat(report.identifiers()).containsExactly(Map.entry("实例", "order-7d9f"));
    }

    /**
     * 标识值同样经过脱敏：使用方可能顺手把带密钥的环境变量填进来。
     */
    @Test
    void shouldMaskSensitiveIdentifierValues() {
        ChaosFeatureReport report = build(Map.of("deploy-token", "abcdef0123456789"), List.of());

        assertThat(report.identifiers().get("deploy-token")).isNotEqualTo("abcdef0123456789");
    }

    /**
     * 空 key 与 null 值不会渲染成 "=null" 这种噪音。
     */
    @Test
    void shouldSkipBlankKeyAndNullValue() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("  ", "ignored");
        configured.put("版本", null);

        ChaosFeatureReport report = build(configured, List.of());

        assertThat(report.identifiers()).containsExactly(Map.entry("版本", ""));
    }

    /**
     * banner 必须在 classpath 上、烧入了框架版本号，且保留 Spring 运行期占位符。
     *
     * <p>版本号靠 Maven 资源过滤注入：运行期没有可靠途径拿到框架版本
     * （EnvironmentPostProcessor 需要 spring.factories，本仓库禁用）。过滤配置写错时这里会直接失败。</p>
     */
    @Test
    void bannerShouldCarryFilteredVersionAndKeepSpringPlaceholders() throws IOException {
        String banner;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("com/michael/chaos/banner.txt")) {
            assertThat(in).as("banner.txt 必须在 classpath 上").isNotNull();
            banner = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(banner).doesNotContain("@project.version@");
        assertThat(banner).containsPattern("Chaos Framework ::\\s+\\(v\\d+\\.\\d+\\.\\d+");
        assertThat(banner).contains("${spring-boot.version}");
        assertThat(banner).contains("${spring.application.name:unnamed}");
    }

    /**
     * 诊断规则失败不影响标识收集（两者共用同一次 build）。
     */
    @Test
    void identifiersShouldSurviveFailingDiagnosticRule() {
        ChaosDiagnosticRule failing = context -> {
            throw new IllegalStateException("rule blew up");
        };
        ChaosFeatureReport report = new ChaosFeatureReporter(
                beanFactory, environment, List.of(failing), Map.of("版本", "2.3.1"), List.of()).build();

        assertThat(report.identifiers()).containsEntry("版本", "2.3.1");
        assertThat(report.findings()).isEmpty();
    }

}
