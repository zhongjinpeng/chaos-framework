package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.RequestTiming;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 访问日志过滤器。
 */
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger accessLog = LoggerFactory.getLogger("GATEWAY_ACCESS_LOG");

    private static final long SLOW_REQUEST_NANOS = 1_000_000_000L;

    private static final List<String> EXCLUDED_PATHS =
            List.of("/actuator/**", "/metrics", "/metrics/**");

    private final String appName;

    private final String environment;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 创建 Gateway 请求耗时日志过滤器。
     */
    public GatewayAccessLogFilter(String appName, String environment) {
        this.appName = appName;
        this.environment = environment;
    }

    /**
     * 记录 Gateway 请求方法、路径、响应状态和耗时。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isExcluded(exchange)) {
            return chain.filter(exchange);
        }
        RequestTiming timing = RequestTiming.start();
        exchange.getAttributes().put(RequestTiming.ATTRIBUTE_NAME, timing);
        return chain.filter(exchange).doFinally(signal -> {
            RequestTiming.Snapshot snapshot = timing.snapshot();
            Integer status = exchange.getResponse().getStatusCode() == null
                    ? 0
                    : exchange.getResponse().getStatusCode().value();
            boolean failed = status >= 400 || signal == reactor.core.publisher.SignalType.ON_ERROR;
            boolean slow = snapshot.totalNanos() >= SLOW_REQUEST_NANOS;
            String message = "event=request_timing env={} service={} protocol=http method={} path={} status={} "
                    + "trace_id={} request_id={} total_ms={} stages={} counts={} slow={} slowest_stage={} signal={}";
            Object[] arguments = {
                    environment,
                    appName,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath(),
                    status,
                    exchange.getAttribute(ChaosHeaders.TRACE_ID),
                    // X-Request-Id 由客户端控制，只记录合法格式，防止日志注入。
                    GatewayTraceFilter.safeId(exchange.getRequest().getHeaders().getFirst("X-Request-Id")),
                    snapshot.totalMillis(),
                    snapshot.stageMillis(),
                    snapshot.stageCounts(),
                    slow,
                    snapshot.slowestStage(),
                    signal
            };
            if (failed || slow) {
                accessLog.warn(message, arguments);
            } else {
                accessLog.info(message, arguments);
            }
        });
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private boolean isExcluded(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
