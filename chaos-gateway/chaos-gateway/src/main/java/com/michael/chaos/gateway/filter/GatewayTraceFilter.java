package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.trace.W3cTraceContext;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway trace 请求头透传过滤器。
 */
public class GatewayTraceFilter implements GlobalFilter, Ordered {

    /**
     * 兼容旧版 {@code X-Trace-Id} 的合法格式：字母、数字、下划线和短横线，最长 64 位。
     *
     * <p>该值会写回响应头、访问日志和 MDC，不校验时客户端可以注入超长或包含日志分隔符的内容。</p>
     */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /**
     * 为进入网关的请求补齐 traceId 和 spanId，并写回响应头。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Optional<W3cTraceContext> upstreamW3cContext = W3cTraceContext.parse(firstHeader(request, ChaosHeaders.TRACEPARENT));
        String traceId = upstreamW3cContext
                .map(W3cTraceContext::traceId)
                .orElseGet(() -> resolveTraceId(request));
        String spanId = TraceContext.newSpanId();
        Optional<W3cTraceContext> outgoingW3cContext = W3cTraceContext.from(traceId, spanId);
        ServerHttpRequest mutated = request.mutate().headers(headers -> {
                    headers.set(ChaosHeaders.TRACE_ID, traceId);
                    headers.set(ChaosHeaders.SPAN_ID, spanId);
                    outgoingW3cContext
                            .map(W3cTraceContext::traceparent)
                            .ifPresent(value -> headers.set(ChaosHeaders.TRACEPARENT, value));
                    copyIfPresent(request, ChaosHeaders.TRACESTATE, headers::set);
                    copyIfPresent(request, ChaosHeaders.BAGGAGE, headers::set);
                })
                .build();
        exchange.getAttributes().put(ChaosHeaders.TRACE_ID, traceId);
        exchange.getResponse().beforeCommit(() -> {
            publishExternalTraceHeader(exchange.getResponse().getHeaders(), traceId);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 读取请求头第一个值。
     */
    private String firstHeader(ServerHttpRequest request, String name) {
        return request.getHeaders().getFirst(name);
    }

    private void publishExternalTraceHeader(HttpHeaders headers, String traceId) {
        headers.set(ChaosHeaders.TRACE_ID, traceId);
        headers.remove(ChaosHeaders.SPAN_ID);
        headers.remove(ChaosHeaders.TRACEPARENT);
        headers.remove(ChaosHeaders.TRACESTATE);
        headers.remove(ChaosHeaders.BAGGAGE);
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = safeId(firstHeader(request, ChaosHeaders.TRACE_ID));
        return traceId == null ? TraceContext.newId() : traceId;
    }

    /**
     * 校验外部传入的标识类请求头；不合法时返回 {@code null}。
     */
    static String safeId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private void copyIfPresent(ServerHttpRequest request, String name, HeaderSetter setter) {
        String value = firstHeader(request, name);
        if (value != null && !value.isBlank()) {
            setter.set(name, value);
        }
    }

    @FunctionalInterface
    private interface HeaderSetter {

        /**
         * 设置请求头。
         */
        void set(String name, String value);
    }
}
