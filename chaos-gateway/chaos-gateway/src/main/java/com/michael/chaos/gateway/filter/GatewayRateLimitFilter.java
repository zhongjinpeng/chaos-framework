package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimitKeyResolver;
import com.michael.chaos.gateway.ratelimit.GatewayRateLimiter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 全局限流过滤器。
 */
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitFilter.class);

    private final ChaosGatewayProperties properties;

    private final GatewayRateLimiter rateLimiter;

    private final GatewayRateLimitKeyResolver keyResolver;

    private final ChaosMetrics metrics;

    /**
     * 创建 Gateway 限流过滤器，不上报指标。
     */
    public GatewayRateLimitFilter(
            ChaosGatewayProperties properties,
            GatewayRateLimiter rateLimiter,
            GatewayRateLimitKeyResolver keyResolver) {
        this(properties, rateLimiter, keyResolver, null);
    }

    /**
     * 创建 Gateway 限流过滤器。
     *
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public GatewayRateLimitFilter(
            ChaosGatewayProperties properties,
            GatewayRateLimiter rateLimiter,
            GatewayRateLimitKeyResolver keyResolver,
            ChaosMetrics metrics) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
    }

    /**
     * 对未命中 {@code rate-limit.skip-paths} 的请求执行全局限流。
     *
     * <p>跳过规则与鉴权白名单分离：登录、验证码等接口一般在鉴权白名单中，如果共用白名单，
     * 暴力破解和撞库请求将完全不受限流保护。</p>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getRateLimit().isEnabled()
                || GatewayWhitelistMatcher.matchesAny(exchange, properties.getRateLimit().getSkipPaths())) {
            return chain.filter(exchange);
        }
        RateLimitRule rule = resolveRule(exchange);
        String key = keyResolver.resolve(exchange, rule.id(), rule.keyTypes());
        return rateLimiter.tryAcquire(new RateLimitContext(key, rule.permitsPerSecond()))
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    metrics.increment(ChaosMeterNames.RATE_LIMIT_REJECTED,
                            ChaosMeterNames.TAG_SOURCE, "gateway",
                            ChaosMeterNames.TAG_DIMENSION, rule.id());
                    return GatewayErrorResponseWriter.tooManyRequests(exchange);
                })
                .onErrorResume(ex -> {
                    // fail-open 时限流会静默失效，必须至少留下告警日志和指标供监控采集。
                    boolean failOpen = properties.getRateLimit().isFailOpen();
                    log.warn("Rate limiter error for key [{}], failOpen={}: {}", key, failOpen, ex.getMessage());
                    metrics.increment(ChaosMeterNames.RATE_LIMIT_ERRORS,
                            ChaosMeterNames.TAG_SOURCE, "gateway",
                            ChaosMeterNames.TAG_OUTCOME, failOpen ? "fail-open" : "fail-closed");
                    if (failOpen) {
                        return chain.filter(exchange);
                    }
                    return GatewayErrorResponseWriter.tooManyRequests(exchange);
                });
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 40;
    }

    private RateLimitRule resolveRule(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        for (ChaosGatewayProperties.Rule rule : properties.getRateLimit().getRules()) {
            if (GatewayWhitelistMatcher.matches(rule.getPathPattern(), path)) {
                List<ChaosGatewayProperties.KeyType> keyTypes = rule.getKeyTypes().isEmpty()
                        ? properties.getRateLimit().getKeyTypes()
                        : rule.getKeyTypes();
                return new RateLimitRule(rule.getId(), rule.getPermitsPerSecond(), keyTypes);
            }
        }
        return new RateLimitRule(
                "default",
                properties.getRateLimit().getDefaultPermitsPerSecond(),
                properties.getRateLimit().getKeyTypes()
        );
    }

    private record RateLimitRule(
            String id,
            int permitsPerSecond,
            List<ChaosGatewayProperties.KeyType> keyTypes
    ) {
    }
}
