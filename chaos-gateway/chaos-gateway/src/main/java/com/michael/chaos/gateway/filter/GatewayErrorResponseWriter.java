package com.michael.chaos.gateway.filter;

import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.core.exception.ErrorCode;
import com.michael.chaos.trace.TraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 错误响应写入器。
 *
 * <p>WebFlux 网关不依赖 WebMVC 的 Result 包装能力，因此在网关侧直接输出同结构 JSON。
 * 字段集合由 {@code GatewayErrorResponseShapeTest} 与 {@code Result} 记录逐字段比对，防止两边悄悄漂移。</p>
 *
 * <p>文案按请求的 {@code Accept-Language} 解析，与 chaos-web 共用 chaos-core 中的 {@code chaos.error.common.*} 消息 key。
 * 这里不复用 chaos-web 的 {@code MessageSourceErrorMessageResolver}：那是 spring-context 的 Servlet 侧组件，
 * 网关不应为了几条固定文案引入它；直接读 {@link ResourceBundle} 即可，代价只有一次缓存查找。</p>
 */
final class GatewayErrorResponseWriter {

    /**
     * 与 chaos-web 共用的错误码文案资源包（位于 chaos-core）。
     */
    private static final String BUNDLE = "com.michael.chaos.core.i18n.messages.chaos-core";

    /**
     * 按语言缓存已解析的资源包；{@code ResourceBundle.getBundle} 自带缓存，但仍要走一次 key 构造与同步块。
     * 缺资源包时缓存空 Optional，避免每个错误响应都重复尝试加载。
     */
    private static final ConcurrentHashMap<Locale, java.util.Optional<ResourceBundle>> BUNDLES =
            new ConcurrentHashMap<>();

    private GatewayErrorResponseWriter() {
    }

    /**
     * 写入 400 响应。
     */
    static Mono<Void> badRequest(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.BAD_REQUEST, CommonErrorCode.BAD_REQUEST);
    }

    /**
     * 写入 401 响应。
     */
    static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 写入 403 响应。
     */
    static Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN);
    }

    /**
     * 写入 429 响应。
     */
    static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, CommonErrorCode.TOO_MANY_REQUESTS);
    }

    /**
     * 写入 503 响应。
     */
    public static Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    /**
     * 写入 500 响应。
     */
    public static Mono<Void> internalError(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR);
    }

    /**
     * 写入指定错误响应。
     */
    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = json(errorCode, localize(errorCode, exchange)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static String json(ErrorCode errorCode, String message) {
        return """
                {"code":"%s","message":"%s","data":null,"traceId":"%s","timestamp":%d}"""
                .formatted(
                        escape(errorCode.code()),
                        escape(message),
                        escape(TraceContext.traceId()),
                        Instant.now().toEpochMilli()
                );
    }

    /**
     * 按请求语言解析错误码文案，查不到时退回错误码默认消息。
     */
    static String localize(ErrorCode errorCode, ServerWebExchange exchange) {
        String key = errorCode.messageKey();
        if (key == null || key.isBlank()) {
            return errorCode.message();
        }
        Locale locale = resolveLocale(exchange);
        return BUNDLES.computeIfAbsent(locale, GatewayErrorResponseWriter::loadBundle)
                .filter(bundle -> bundle.containsKey(key))
                .map(bundle -> bundle.getString(key))
                .orElseGet(errorCode::message);
    }

    private static java.util.Optional<ResourceBundle> loadBundle(Locale locale) {
        try {
            // 关闭 fallbackToSystemLocale 的等价做法：未命中的语言确定性地回退到英文根资源包，
            // 而不是跟着部署机器的 locale 漂移。
            return java.util.Optional.of(ResourceBundle.getBundle(BUNDLE, locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)));
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private static Locale resolveLocale(ServerWebExchange exchange) {
        try {
            Locale locale = exchange.getLocaleContext().getLocale();
            return locale == null ? Locale.ROOT : locale;
        } catch (RuntimeException ex) {
            return Locale.ROOT;
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}
