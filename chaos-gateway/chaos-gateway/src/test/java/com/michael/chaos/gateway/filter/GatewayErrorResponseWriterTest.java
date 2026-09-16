package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michael.chaos.core.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * Gateway 错误响应写入器测试。
 *
 * <p>这些断言此前由 {@code LayerBoundaryArchitectureTest} 用"源码里出现过 traceId 这四个字符"代替，
 * 实际上既不校验 JSON 能否解析，也不校验状态码和字段。这里按真实响应断言。</p>
 */
class GatewayErrorResponseWriterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static MockServerWebExchange exchange(String acceptLanguage) {
        MockServerHttpRequest request = acceptLanguage == null
                ? MockServerHttpRequest.get("/api/orders").build()
                : MockServerHttpRequest.get("/api/orders").header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage).build();
        return MockServerWebExchange.from(request);
    }

    private static JsonNode body(MockServerWebExchange exchange) throws Exception {
        String json = exchange.getResponse().getBodyAsString().block();
        assertThat(json).isNotNull();
        return OBJECT_MAPPER.readTree(json);
    }

    /**
     * 401 响应必须是可解析的 JSON，带正确状态码、内容类型和完整字段。
     */
    @Test
    void shouldWriteParsableJsonWithAllResultFields() throws Exception {
        MockServerWebExchange exchange = exchange(null);

        StepVerifier.create(GatewayErrorResponseWriter.unauthorized(exchange)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode json = body(exchange);
        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "data", "traceId", "timestamp");
        assertThat(json.get("code").asText()).isEqualTo("401");
        assertThat(json.get("data").isNull()).isTrue();
        assertThat(json.get("timestamp").asLong()).isPositive();
    }

    /**
     * 各状态码使用与之一致的错误码。
     */
    @Test
    void shouldUseErrorCodeMatchingStatus() throws Exception {
        MockServerWebExchange tooMany = exchange(null);
        MockServerWebExchange unavailable = exchange(null);
        MockServerWebExchange forbidden = exchange(null);

        StepVerifier.create(GatewayErrorResponseWriter.tooManyRequests(tooMany)).verifyComplete();
        StepVerifier.create(GatewayErrorResponseWriter.serviceUnavailable(unavailable)).verifyComplete();
        StepVerifier.create(GatewayErrorResponseWriter.forbidden(forbidden)).verifyComplete();

        assertThat(tooMany.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body(tooMany).get("code").asText()).isEqualTo("429");
        assertThat(unavailable.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body(unavailable).get("code").asText()).isEqualTo("503");
        assertThat(forbidden.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(forbidden).get("code").asText()).isEqualTo("403");
    }

    /**
     * 文案按 Accept-Language 切换，与 Servlet 侧共用 chaos-core 的错误码资源包。
     */
    @Test
    void shouldLocalizeMessageByAcceptLanguage() throws Exception {
        MockServerWebExchange chinese = exchange("zh-CN");
        MockServerWebExchange english = exchange("en-US");

        StepVerifier.create(GatewayErrorResponseWriter.tooManyRequests(chinese)).verifyComplete();
        StepVerifier.create(GatewayErrorResponseWriter.tooManyRequests(english)).verifyComplete();

        assertThat(body(chinese).get("message").asText()).isEqualTo("请求过于频繁，请稍后重试");
        assertThat(body(english).get("message").asText()).isEqualTo("too many requests");
    }

    /**
     * 未知语言确定性回退到英文根资源包，不跟随部署机器的默认语言漂移。
     */
    @Test
    void shouldFallBackToRootBundleForUnknownLocale() {
        assertThat(GatewayErrorResponseWriter.localize(CommonErrorCode.NOT_FOUND, exchange("ja-JP")))
                .isEqualTo("not found");
    }

    /**
     * 响应已提交时不再写入，避免 IllegalStateException 打断过滤器链。
     */
    @Test
    void shouldSkipWhenResponseAlreadyCommitted() {
        MockServerWebExchange exchange = exchange(null);
        exchange.getResponse().setComplete().block();

        StepVerifier.create(GatewayErrorResponseWriter.badRequest(exchange)).verifyComplete();
    }

    /**
     * 文案中的引号、反斜杠和控制字符必须转义，否则响应体不是合法 JSON。
     */
    @Test
    void shouldEscapeSpecialCharacters() throws Exception {
        MockServerWebExchange exchange = exchange(null);

        StepVerifier.create(GatewayErrorResponseWriter.write(exchange, HttpStatus.BAD_REQUEST,
                new QuotingErrorCode())).verifyComplete();

        // 能被 Jackson 解析且内容还原，就证明转义正确：漏转义会直接导致解析失败。
        assertThat(body(exchange).get("message").asText()).isEqualTo("say \"hi\"\\\n");
    }

    /**
     * 消息里带引号、反斜杠和换行的错误码，用于验证转义。
     */
    private static final class QuotingErrorCode implements com.michael.chaos.core.exception.ErrorCode {

        @Override
        public String code() {
            return "400";
        }

        @Override
        public String message() {
            return "say \"hi\"\\\n";
        }

        @Override
        public String messageKey() {
            return "";
        }
    }
}
