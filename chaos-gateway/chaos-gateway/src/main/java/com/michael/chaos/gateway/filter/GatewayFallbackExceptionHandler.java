package com.michael.chaos.gateway.filter;

import com.michael.chaos.gateway.config.ChaosGatewayProperties;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Gateway 统一降级异常处理器。
 *
 * <p>WebFlux 网关转发、过滤链或下游连接异常时，由该处理器统一输出 JSON，避免把底层异常泄漏给调用方。</p>
 */
public class GatewayFallbackExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private final ChaosGatewayProperties properties;

    /**
     * 创建 Gateway 降级异常处理器。
     */
    public GatewayFallbackExceptionHandler(ChaosGatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 将网关转发异常转换为统一 JSON。
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!properties.getFallback().isEnabled() || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (isUnavailable(ex)) {
            return GatewayErrorResponseWriter.serviceUnavailable(exchange);
        }
        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status == null || status.is5xxServerError()) {
                return GatewayErrorResponseWriter.serviceUnavailable(exchange);
            }
            return Mono.error(ex);
        }
        if (properties.getFallback().isIncludeUnhandled()) {
            return GatewayErrorResponseWriter.internalError(exchange);
        }
        return Mono.error(ex);
    }

    /**
     * 确保降级处理器早于默认错误处理器执行。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isUnavailable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof TimeoutException
                    || current instanceof UnknownHostException
                    || current instanceof SocketException
                    || current instanceof SocketTimeoutException
                    || current instanceof ClosedChannelException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
