package com.michael.chaos.autoconfigure.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.DisabledCategory;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Severity;
import com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * 内置诊断规则、脱敏与功能目录测试。
 */
class ChaosBuiltInDiagnosticRulesTest {

    private final MockEnvironment environment = new MockEnvironment();

    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

    /**
     * Redis 已启用但未配置 key 前缀时告警。
     */
    @Test
    void redisKeyPrefixRule() {
        assertThat(ChaosBuiltInDiagnosticRules.redisKeyPrefix(context(false, "redis")))
                .singleElement().extracting(Finding::severity).isEqualTo(Severity.WARN);

        environment.setProperty("chaos.redis.key-prefix", "order-service");
        assertThat(ChaosBuiltInDiagnosticRules.redisKeyPrefix(context(false, "redis"))).isEmpty();
        assertThat(ChaosBuiltInDiagnosticRules.redisKeyPrefix(context(false))).isEmpty();
    }

    /**
     * 非生产环境使用内存实现时提前提示，生产模式交给生产安全检查。
     */
    @Test
    void developmentOnlyImplementationsRule() {
        beanFactory.registerSingleton("rateLimiter", new InMemoryRateLimiter());

        assertThat(ChaosBuiltInDiagnosticRules.developmentOnlyImplementations(context(false)))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo(Severity.INFO);
                    assertThat(finding.problem()).contains("InMemoryRateLimiter");
                    assertThat(finding.fix()).contains("chaos-redis-starter");
                });
        assertThat(ChaosBuiltInDiagnosticRules.developmentOnlyImplementations(context(true))).isEmpty();
    }

    /**
     * 网关 JWT 未配置 issuer 或 audiences 时告警；关闭验签或使用 opaque 时不提示。
     */
    @Test
    void gatewayJwtIssuerAndAudienceRule() {
        assertThat(ChaosBuiltInDiagnosticRules.gatewayJwtIssuerAndAudience(context(false, "gateway"))).hasSize(1);

        environment.setProperty("chaos.gateway.jwt.issuer-uri", "https://auth.example.com");
        environment.setProperty("chaos.gateway.jwt.audiences[0]", "gateway");
        assertThat(ChaosBuiltInDiagnosticRules.gatewayJwtIssuerAndAudience(context(false, "gateway"))).isEmpty();

        MockEnvironment opaque = new MockEnvironment().withProperty("chaos.gateway.token.type", "opaque");
        assertThat(ChaosBuiltInDiagnosticRules.gatewayJwtIssuerAndAudience(
                new ChaosDiagnosticContext(opaque, beanFactory, false, Set.of("gateway")))).isEmpty();
    }

    /**
     * 部署在代理之后却没有可信代理时提示；开启信任身份头却没有可信代理时告警。
     */
    @Test
    void trustedProxiesRule() {
        environment.setProperty("server.forward-headers-strategy", "native");
        assertThat(ChaosBuiltInDiagnosticRules.trustedProxies(context(false, "gateway", "web")))
                .extracting(Finding::feature).containsExactly("gateway", "web");

        MockEnvironment identity = new MockEnvironment()
                .withProperty("chaos.web.forwarding.trust-identity-headers", "true");
        assertThat(ChaosBuiltInDiagnosticRules.trustedProxies(
                new ChaosDiagnosticContext(identity, beanFactory, false, Set.of("web"))))
                .singleElement().extracting(Finding::severity).isEqualTo(Severity.WARN);

        MockEnvironment configured = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("chaos.web.forwarding.trusted-proxies", "10.0.0.0/8");
        assertThat(ChaosBuiltInDiagnosticRules.trustedProxies(
                new ChaosDiagnosticContext(configured, beanFactory, false, Set.of("web")))).isEmpty();
    }

    /**
     * 生产模式下放宽生产安全检查时告警。
     */
    @Test
    void productionSafetyRelaxedRule() {
        environment.setProperty("chaos.production-safety.fail-fast", "false");
        environment.setProperty("chaos.production-safety.allow-unsafe-defaults", "true");

        assertThat(ChaosBuiltInDiagnosticRules.productionSafetyRelaxed(context(true))).hasSize(2);
        assertThat(ChaosBuiltInDiagnosticRules.productionSafetyRelaxed(context(false))).isEmpty();
    }

    /**
     * 网关应用的类路径中存在 chaos-web 时提示（测试类路径同时包含两者）。
     */
    @Test
    void servletLibraryInReactiveApplicationRule() {
        assertThat(ChaosBuiltInDiagnosticRules.servletLibraryInReactiveApplication(context(false, "gateway")))
                .singleElement().extracting(Finding::fix).asString().contains("chaos-gateway-starter");
        assertThat(ChaosBuiltInDiagnosticRules.servletLibraryInReactiveApplication(context(false, "web"))).isEmpty();
    }

    /**
     * 敏感键名整体掩码，URL 只保留 scheme、host、port。
     */
    @Test
    void maskerShouldHideCredentials() {
        assertThat(ChaosSettingMasker.mask("chaos.gateway.opaque-token.client-secret", "s3cr3t")).isEqualTo("******");
        assertThat(ChaosSettingMasker.mask("spring.datasource.password", "p")).isEqualTo("******");
        assertThat(ChaosSettingMasker.mask("access-token", "abc")).isEqualTo("******");
        assertThat(ChaosSettingMasker.mask("token.type", "JWT")).isEqualTo("JWT");
        assertThat(ChaosSettingMasker.mask("key-prefix", "order")).isEqualTo("order");
        assertThat(ChaosSettingMasker.endpoint("https://user:pass@auth.example.com:9443/oauth2/jwks?token=x"))
                .isEqualTo("https://auth.example.com:9443");
        assertThat(ChaosSettingMasker.endpoint("not a url")).isEqualTo("******");
    }

    /**
     * 条件消息归类。
     */
    @Test
    void shouldCategorizeConditionMessages() {
        assertThat(ChaosFeatureReporter.categorize("@ConditionalOnClass did not find required class 'x'"))
                .isEqualTo(DisabledCategory.MISSING_DEPENDENCY);
        assertThat(ChaosFeatureReporter.categorize("did not find reactive web application classes"))
                .isEqualTo(DisabledCategory.WEB_APPLICATION_TYPE);
        assertThat(ChaosFeatureReporter.categorize("@ConditionalOnProperty (chaos.job.enabled=true) found different value"))
                .isEqualTo(DisabledCategory.DISABLED_BY_PROPERTY);
    }

    /**
     * 每个自动装配（诊断自身除外）都必须在功能目录中登记，否则启动报告会漏掉该功能。
     */
    @Test
    void catalogShouldCoverEveryAutoConfiguration() throws IOException {
        List<String> imports;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            imports = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> !line.equals(ChaosDiagnosticsAutoConfiguration.class.getName()))
                    .toList();
        }

        assertThat(ChaosFeatureCatalog.entries())
                .extracting(ChaosFeatureCatalog.Entry::autoConfigurationClass)
                .containsExactlyInAnyOrderElementsOf(imports);
    }

    /**
     * 中英文标签按显示宽度对齐。
     */
    @Test
    void padShouldAlignByDisplayWidth() {
        assertThat(ChaosStartupReportRenderer.pad("gateway")).hasSize(17);
        assertThat(ChaosStartupReportRenderer.pad("缺少依赖")).hasSize(13);
    }

    /**
     * JDBC 审计开启但仍同步写入时告警；开启异步后不再提示。
     */
    @Test
    void synchronousJdbcAuditRule() {
        assertThat(ChaosBuiltInDiagnosticRules.synchronousJdbcAudit(context(false))).isEmpty();

        environment.setProperty("chaos.audit.jdbc.enabled", "true");
        assertThat(ChaosBuiltInDiagnosticRules.synchronousJdbcAudit(context(false)))
                .singleElement().extracting(Finding::severity).isEqualTo(Severity.WARN);

        environment.setProperty("chaos.audit.async.enabled", "true");
        assertThat(ChaosBuiltInDiagnosticRules.synchronousJdbcAudit(context(false))).isEmpty();
    }

    private ChaosDiagnosticContext context(boolean productionMode, String... enabledFeatures) {
        return new ChaosDiagnosticContext(environment, beanFactory, productionMode, Set.of(enabledFeatures));
    }
}
