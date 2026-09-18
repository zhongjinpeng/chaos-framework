package com.michael.chaos.autoconfigure.diagnostics;

import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Finding;
import com.michael.chaos.autoconfigure.diagnostics.ChaosFeatureReport.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.ClassUtils;

/**
 * 内置诊断规则。
 *
 * <p>每条规则只针对"很常见、启动时就能判断、但不值得阻断启动"的配置遗漏；必须阻断的问题
 * （例如生产环境使用内存实现）由生产安全检查负责，这里只做上线前的提前提示。</p>
 */
public final class ChaosBuiltInDiagnosticRules {

    static final String IN_MEMORY_RATE_LIMITER = "com.michael.chaos.core.ratelimit.support.InMemoryRateLimiter";

    static final String IN_MEMORY_IDEMPOTENT_REPOSITORY =
            "com.michael.chaos.core.idempotent.support.InMemoryIdempotentRepository";

    static final String IN_MEMORY_IDEMPOTENT_RECORD_STORE =
            "com.michael.chaos.core.idempotent.support.InMemoryIdempotentRecordStore";

    static final String NOOP_JWT_REVOCATION_SERVICE = "com.michael.chaos.security.api.token.NoopJwtRevocationService";

    static final String CHAOS_WEB_MARKER = "com.michael.chaos.web.filter.TraceFilter";

    static final String AUTHORIZATION_POLICY_SOURCE = "com.michael.chaos.security.api.access.AuthorizationPolicySource";

    private ChaosBuiltInDiagnosticRules() {
    }

    /**
     * 返回全部内置规则。
     */
    public static List<ChaosDiagnosticRule> all() {
        return List.of(
                ChaosBuiltInDiagnosticRules::redisKeyPrefix,
                ChaosBuiltInDiagnosticRules::developmentOnlyImplementations,
                ChaosBuiltInDiagnosticRules::gatewayJwtIssuerAndAudience,
                ChaosBuiltInDiagnosticRules::trustedProxies,
                ChaosBuiltInDiagnosticRules::productionSafetyRelaxed,
                ChaosBuiltInDiagnosticRules::servletLibraryInReactiveApplication,
                ChaosBuiltInDiagnosticRules::synchronousJdbcAudit,
                ChaosBuiltInDiagnosticRules::missingAuthorizationPolicySource);
    }

    /**
     * Redis key 前缀为空：多个服务共用 Redis 时幂等、锁、限流 key 会互相覆盖。
     */
    static List<Finding> redisKeyPrefix(ChaosDiagnosticContext context) {
        if (!context.isEnabled("redis") || !context.property("chaos.redis.key-prefix").isEmpty()) {
            return List.of();
        }
        return List.of(new Finding(Severity.WARN, "redis",
                "chaos.redis.key-prefix 未配置，多个服务共用同一 Redis 时幂等、分布式锁、限流 key 可能互相覆盖",
                "配置 chaos.redis.key-prefix=${spring.application.name}"));
    }

    /**
     * 非生产环境使用了只适用于开发的实现：现在能跑，但切到生产 profile 后会被生产安全检查阻断启动。
     */
    static List<Finding> developmentOnlyImplementations(ChaosDiagnosticContext context) {
        if (context.productionMode()) {
            return List.of();
        }
        List<String> implementations = new ArrayList<>();
        if (context.hasBeanOfType(IN_MEMORY_RATE_LIMITER)) {
            implementations.add("InMemoryRateLimiter");
        }
        if (context.hasBeanOfType(IN_MEMORY_IDEMPOTENT_REPOSITORY)) {
            implementations.add("InMemoryIdempotentRepository");
        }
        if (context.hasBeanOfType(IN_MEMORY_IDEMPOTENT_RECORD_STORE)) {
            implementations.add("InMemoryIdempotentRecordStore");
        }
        if (context.hasBeanOfType(NOOP_JWT_REVOCATION_SERVICE)) {
            implementations.add("NoopJwtRevocationService");
        }
        if (implementations.isEmpty()) {
            return List.of();
        }
        return List.of(new Finding(Severity.INFO, "production-safety",
                "当前使用开发用实现 " + String.join("、", implementations) + "，以生产 profile 启动时会被生产安全检查阻断",
                "上线前引入 chaos-redis-starter 并配置 spring.data.redis.*，Redis 实现会自动替换这些兜底实现"));
    }

    /**
     * 声明了 Redis 策略来源，但容器里没有任何 {@code AuthorizationPolicySource}。
     *
     * <p>这种组合不会报错，只是所有动态策略静默失效——写在 Redis 里的拒绝规则一条都不生效，
     * 而日志里什么都看不到。最常见的原因是没引入 chaos-redis-starter（没有 StringRedisTemplate）。</p>
     */
    static List<Finding> missingAuthorizationPolicySource(ChaosDiagnosticContext context) {
        if (!"REDIS".equals(normalize(context.property("chaos.security.access.policy-source"), "CONFIG"))
                || context.hasBeanOfType(AUTHORIZATION_POLICY_SOURCE)) {
            return List.of();
        }
        return List.of(new Finding(Severity.WARN, "security",
                "chaos.security.access.policy-source=redis 但容器中没有 AuthorizationPolicySource，"
                        + "Redis 里的授权策略全部不生效（包括 DENY 规则）",
                "引入 chaos-redis-starter 并配置 spring.data.redis.*，或把 chaos.security.access.policy-source 改回 config"));
    }

    /**
     * JDBC 审计已开启但仍是同步写入：每次登录、每次权限拒绝都会在请求线程内多一次数据库写入。
     */
    static List<Finding> synchronousJdbcAudit(ChaosDiagnosticContext context) {
        if (!context.booleanProperty("chaos.audit.jdbc.enabled", false)
                || context.booleanProperty("chaos.audit.async.enabled", false)) {
            return List.of();
        }
        return List.of(new Finding(Severity.WARN, "audit",
                "chaos.audit.jdbc 已开启但审计仍同步写入，每次登录和权限拒绝都会在请求线程内多一次数据库写入，"
                        + "扫描器批量请求未授权接口时会放大为持续写库",
                "配置 chaos.audit.async.enabled=true 改为异步发布（队列满时丢弃并计入指标，不阻塞业务）"));
    }

    /**
     * 网关开启 JWT 验签但没有限定签发方或受众。
     */
    static List<Finding> gatewayJwtIssuerAndAudience(ChaosDiagnosticContext context) {
        if (!context.isEnabled("gateway")
                || !context.booleanProperty("chaos.gateway.auth-enabled", true)
                || !context.booleanProperty("chaos.gateway.jwt.validation-enabled", true)
                || !"JWT".equals(normalize(context.property("chaos.gateway.token.type"), "JWT"))) {
            return List.of();
        }
        if (!context.property("chaos.gateway.jwt.issuer-uri").isEmpty()
                && !context.listProperty("chaos.gateway.jwt.audiences").isEmpty()) {
            return List.of();
        }
        return List.of(new Finding(Severity.WARN, "gateway",
                "网关 JWT 校验未限定签发方或受众，同一 JWKS 为其他客户端签发的 token 也能通过网关",
                "配置 chaos.gateway.jwt.issuer-uri（与授权服务器 chaos.authorization.issuer 一致）和 chaos.gateway.jwt.audiences"));
    }

    /**
     * 部署在代理之后却没有配置可信代理：客户端 IP 取到的是代理地址，黑名单与按 IP 限流失效；
     * 服务开启了信任身份头却没有可信代理时，身份头永远不会被采纳。
     */
    static List<Finding> trustedProxies(ChaosDiagnosticContext context) {
        List<Finding> findings = new ArrayList<>();
        String strategy = normalize(context.property("server.forward-headers-strategy"), "NONE");
        boolean behindProxy = !"NONE".equals(strategy);
        if (behindProxy && context.isEnabled("gateway") && context.listProperty("chaos.gateway.trusted-proxies").isEmpty()) {
            findings.add(new Finding(Severity.INFO, "gateway",
                    "已配置 server.forward-headers-strategy，但 chaos.gateway.trusted-proxies 为空，网关不会采信 X-Forwarded-For",
                    "把负载均衡/Ingress 的网段加入 chaos.gateway.trusted-proxies，例如 10.0.0.0/8"));
        }
        boolean webTrustedEmpty = context.listProperty("chaos.web.forwarding.trusted-proxies").isEmpty();
        if (behindProxy && context.isEnabled("web") && webTrustedEmpty) {
            findings.add(new Finding(Severity.INFO, "web",
                    "已配置 server.forward-headers-strategy，但 chaos.web.forwarding.trusted-proxies 为空，限流与日志中的客户端 IP 为代理地址",
                    "把网关或负载均衡的网段加入 chaos.web.forwarding.trusted-proxies"));
        }
        if (context.isEnabled("web")
                && context.booleanProperty("chaos.web.forwarding.trust-identity-headers", false)
                && webTrustedEmpty) {
            findings.add(new Finding(Severity.WARN, "web",
                    "开启了 chaos.web.forwarding.trust-identity-headers，但没有配置可信代理，身份头不会被采纳",
                    "配置 chaos.web.forwarding.trusted-proxies 为网关所在网段；服务可被外部直接访问时不要开启信任身份头"));
        }
        return findings;
    }

    /**
     * 生产模式下放宽了生产安全检查。
     */
    static List<Finding> productionSafetyRelaxed(ChaosDiagnosticContext context) {
        if (!context.productionMode()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        if (!context.booleanProperty("chaos.production-safety.fail-fast", true)) {
            findings.add(new Finding(Severity.WARN, "production-safety",
                    "生产模式下 chaos.production-safety.fail-fast=false，危险的开发用实现只会告警、不会阻断启动",
                    "迁移完成后删除该配置，恢复默认 fail-fast"));
        }
        if (context.booleanProperty("chaos.production-safety.allow-unsafe-defaults", false)) {
            findings.add(new Finding(Severity.WARN, "production-safety",
                    "生产模式下 chaos.production-safety.allow-unsafe-defaults=true，生产安全检查被整体跳过",
                    "迁移完成后删除该配置"));
        }
        return findings;
    }

    /**
     * 响应式应用中引入了只适用于 Servlet 的 chaos-web：统一响应、全局异常、限流拦截器都不会生效。
     */
    static List<Finding> servletLibraryInReactiveApplication(ChaosDiagnosticContext context) {
        if (!context.isEnabled("gateway")
                || !ClassUtils.isPresent(CHAOS_WEB_MARKER, ChaosBuiltInDiagnosticRules.class.getClassLoader())) {
            return List.of();
        }
        return List.of(new Finding(Severity.WARN, "web",
                "网关（WebFlux）应用的类路径中存在 chaos-web（Servlet），chaos-web 的过滤器与拦截器不会生效",
                "网关服务移除 chaos-web-starter / chaos-web-service-starter，只保留 chaos-gateway-starter"));
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }
}
