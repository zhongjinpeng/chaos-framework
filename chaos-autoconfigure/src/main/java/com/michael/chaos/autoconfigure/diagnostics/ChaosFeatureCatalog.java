package com.michael.chaos.autoconfigure.diagnostics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.util.ClassUtils;

/**
 * Chaos 功能目录：自动装配类与功能名、关键配置的对应关系。
 *
 * <p>功能名与 starter 名保持一致（{@code web} 对应 chaos-web-starter），使用方看到报告就知道该加哪个 starter。
 * 新增自动装配时需要在这里登记，{@code ChaosFeatureCatalogTest} 会校验 imports 清单中的每个自动装配都已登记。</p>
 */
public final class ChaosFeatureCatalog {

    private static final String BASE = "com.michael.chaos.autoconfigure.";

    private static final List<Entry> ENTRIES = List.of(
            entry("web", "web.ChaosWebAutoConfiguration", context -> settings(
                    "rate-limiter", beanTypes(context, "com.michael.chaos.core.ratelimit.RateLimiter"),
                    "idempotent-repository", beanTypes(context, "com.michael.chaos.core.idempotent.IdempotentRepository"),
                    "idempotent-replay", value(context, "chaos.web.idempotent.replay.enabled", "false"),
                    "idempotent-record-store", beanTypes(context, "com.michael.chaos.core.idempotent.IdempotentRecordStore"),
                    "trusted-proxies", count(context, "chaos.web.forwarding.trusted-proxies"),
                    "trust-identity-headers", value(context, "chaos.web.forwarding.trust-identity-headers", "false"),
                    "xss-enabled", value(context, "chaos.web.xss-enabled", "false"))),
            entry("application", "service.ChaosServiceAutoConfiguration", context -> Map.of()),
            entry("tenant", "tenant.ChaosTenantAutoConfiguration", context -> settings(
                    "servlet-filter", value(context, "chaos.tenant.servlet-filter.enabled", "false"))),
            entry("audit", "audit.ChaosAuditAutoConfiguration", context -> settings(
                    "publisher", beanTypes(context, "com.michael.chaos.audit.AuditEventPublisher"),
                    "async", value(context, "chaos.audit.async.enabled", "false"))),
            entry("audit-jdbc", "audit.jdbc.ChaosAuditJdbcAutoConfiguration", context -> Map.of()),
            entry("security", "security.ChaosSecurityAutoConfiguration", context -> settings(
                    "token.type", value(context, "chaos.security.token.type", "JWT"),
                    "jwk-set-uri", endpoint(context, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri"),
                    "introspection-uri", endpoint(context, "chaos.security.opaque-token.introspection-uri"),
                    "revocation-service", beanTypes(context, "com.michael.chaos.security.api.token.JwtRevocationService"))),
            entry("security-redis", "security.redis.ChaosSecurityRedisAutoConfiguration", context -> Map.of()),
            entry("authorization", "authorization.ChaosAuthorizationAutoConfiguration", context -> settings(
                    "issuer", endpoint(context, "chaos.authorization.issuer"),
                    "token.type", value(context, "chaos.authorization.token.type", "JWT"),
                    "client.store-type", value(context, "chaos.authorization.client.store-type", "MEMORY"))),
            entry("gateway", "gateway.ChaosGatewayAutoConfiguration", context -> settings(
                    "token.type", value(context, "chaos.gateway.token.type", "JWT"),
                    "jwk-set-uri", endpoint(context, "chaos.gateway.jwt.jwk-set-uri"),
                    "issuer-uri", endpoint(context, "chaos.gateway.jwt.issuer-uri"),
                    "audiences", count(context, "chaos.gateway.jwt.audiences"),
                    "rate-limiter", beanTypes(context, "com.michael.chaos.core.ratelimit.RateLimiter"),
                    "trusted-proxies", count(context, "chaos.gateway.trusted-proxies"))),
            entry("gateway-nacos", "gateway.nacos.ChaosGatewayNacosAutoConfiguration", context -> Map.of()),
            entry("cloud", "cloud.ChaosCloudAutoConfiguration", context -> Map.of()),
            entry("cloud-nacos", "cloud.nacos.ChaosCloudNacosAutoConfiguration", context -> Map.of()),
            entry("mybatis", "mybatis.ChaosMybatisAutoConfiguration", context -> settings(
                    "tenant.enabled", value(context, "chaos.mybatis.tenant.enabled", "true"),
                    "tenant.column", value(context, "chaos.mybatis.tenant.column", "tenant_id"),
                    "pagination.max-limit", value(context, "chaos.mybatis.pagination.max-limit", "500"))),
            entry("redis", "redis.ChaosRedisAutoConfiguration", context -> settings(
                    "key-prefix", value(context, "chaos.redis.key-prefix", "（未配置）"))),
            entry("mq", "mq.ChaosMqAutoConfiguration", context -> settings(
                    "publisher", beanTypes(context, "com.michael.chaos.mq.MessagePublisher"))),
            entry("mq-outbox", "mq.ChaosMqOutboxAutoConfiguration", context -> settings(
                    "dispatcher", context.hasBeanOfType(
                            "com.michael.chaos.autoconfigure.mq.ChaosMqOutboxAutoConfiguration$OutboxDispatchLifecycle")
                            ? "运行" : "未启用",
                    "health", value(context, "chaos.mq.outbox.health.enabled", "true"))),
            entry("metrics", "metrics.ChaosMetricsAutoConfiguration", context -> settings(
                    "reporter", beanTypes(context, "com.michael.chaos.core.metrics.ChaosMetrics"))),
            entry("job", "job.ChaosJobAutoConfiguration", context -> settings(
                    "scheduling-enabled", value(context, "chaos.job.scheduling-enabled", "true"))),
            entry("storage", "storage.ChaosStorageAutoConfiguration", context -> settings(
                    "provider", value(context, "chaos.storage.provider", "NONE")))
    );

    private ChaosFeatureCatalog() {
    }

    /**
     * 返回全部登记项（按展示顺序）。
     */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * 功能登记项。
     *
     * @param feature 功能名
     * @param autoConfigurationClass 自动装配类全限定名
     * @param settings 关键配置提取函数（返回值会再经过 {@link ChaosSettingMasker} 脱敏）
     */
    public record Entry(
            String feature,
            String autoConfigurationClass,
            Function<ChaosDiagnosticContext, Map<String, String>> settings) {
    }

    private static Entry entry(String feature, String simpleName, Function<ChaosDiagnosticContext, Map<String, String>> settings) {
        return new Entry(feature, BASE + simpleName, settings);
    }

    private static Map<String, String> settings(String... keyValues) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private static String value(ChaosDiagnosticContext context, String property, String defaultValue) {
        String value = context.property(property);
        return value.isEmpty() ? defaultValue : ChaosSettingMasker.mask(property, value);
    }

    private static String endpoint(ChaosDiagnosticContext context, String property) {
        String value = ChaosSettingMasker.endpoint(context.property(property));
        return value.isEmpty() ? "（未配置）" : value;
    }

    private static String count(ChaosDiagnosticContext context, String property) {
        return String.valueOf(context.listProperty(property).size());
    }

    private static String beanTypes(ChaosDiagnosticContext context, String className) {
        List<String> names = context.beanNamesOfType(className);
        if (names.isEmpty()) {
            return "无";
        }
        return String.join(", ", names.stream().map(name -> simpleTypeName(context, name)).distinct().toList());
    }

    private static String simpleTypeName(ChaosDiagnosticContext context, String beanName) {
        Class<?> type = context.beanFactory().getType(beanName, false);
        return type == null ? beanName : ClassUtils.getUserClass(type).getSimpleName();
    }
}
