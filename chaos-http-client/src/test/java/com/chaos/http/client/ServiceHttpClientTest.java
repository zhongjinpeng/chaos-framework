package com.chaos.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * 服务间 HTTP 客户端测试。
 */
class ServiceHttpClientTest {

    /**
     * 成功响应应解包 data，并携带幂等键。
     */
    @Test
    void shouldUnwrapDataAndSendIdempotencyKey() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://foundation/internal/x"))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andRespond(withSuccess("{\"code\":\"0\",\"data\":{\"id\":7}}", MediaType.APPLICATION_JSON));

        Map<String, Object> data = new TestClient(rest, fixedToken())
                .post("/internal/x", Map.of(), new ParameterizedTypeReference<>() {
                }, "key-1", "tenant-1", "alice");

        assertEquals(7, ((Number) data.get("id")).intValue());
        server.verify();
    }

    /**
     * 业务错误码交给翻译器，不直接抛出原始 HTTP 异常。
     */
    @Test
    void shouldTranslateBusinessErrorCode() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://foundation/internal/x"))
                .andRespond(withSuccess("{\"code\":\"DICT_MISSING\",\"message\":\"not found\"}",
                        MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TestClient(rest, fixedToken())
                        .post("/internal/x", Map.of(), new ParameterizedTypeReference<Map<String, Object>>() {
                        }, "key-1", "tenant-1", "alice"));

        assertEquals("DICT_MISSING:not found", ex.getMessage());
        server.verify();
    }

    /**
     * 401 应刷新一次令牌并重试，重试成功后不再报错。
     */
    @Test
    void shouldRefreshTokenOnceAfterUnauthorized() {
        AtomicInteger issued = new AtomicInteger();
        AtomicInteger invalidated = new AtomicInteger();
        ServiceTokenProvider tokens = new ServiceTokenProvider() {
            @Override
            public String token() {
                return "token-" + issued.incrementAndGet();
            }

            @Override
            public void invalidate() {
                invalidated.incrementAndGet();
            }
        };
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("http://foundation/internal/x"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo("http://foundation/internal/x"))
                .andRespond(withSuccess("{\"code\":\"0\",\"data\":{\"id\":1}}", MediaType.APPLICATION_JSON));

        Map<String, Object> data = new TestClient(rest, tokens)
                .post("/internal/x", Map.of(), new ParameterizedTypeReference<>() {
                }, "key-1", "tenant-1", "alice");

        assertEquals(1, ((Number) data.get("id")).intValue());
        assertEquals(1, invalidated.get());
        assertEquals(2, issued.get());
        server.verify();
    }

    private static ServiceTokenProvider fixedToken() {
        return new ServiceTokenProvider() {
            @Override
            public String token() {
                return "token";
            }

            @Override
            public void invalidate() {
            }
        };
    }

    private static final class TestClient extends ServiceHttpClient {

        TestClient(RestTemplate rest, ServiceTokenProvider tokens) {
            super("http://foundation", "test", tokens, rest, new ObjectMapper(), 1024 * 1024,
                    (code, message) -> new IllegalStateException(code + ":" + message));
        }
    }
}
