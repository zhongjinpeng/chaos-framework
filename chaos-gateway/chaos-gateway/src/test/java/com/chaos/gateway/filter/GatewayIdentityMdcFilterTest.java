package com.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.trace.log.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

/**
 * Gateway 认证身份 MDC 过滤器测试。
 */
class GatewayIdentityMdcFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 认证属性应在下游链路可见，并在请求结束后清理。
     */
    @Test
    void shouldExposeAuthenticatedIdentityDuringDownstreamChain() {
        GatewayIdentityMdcFilter filter = new GatewayIdentityMdcFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_USER_ID, "1001");
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_TENANT_ID, "tenant-a");

        filter.filter(exchange, current -> {
            assertThat(MDC.get(MdcKeys.USER_ID)).isEqualTo("1001");
            assertThat(MDC.get(MdcKeys.TENANT_ID)).isEqualTo("tenant-a");
            return Mono.empty();
        }).block();

        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
        assertThat(MDC.get(MdcKeys.TENANT_ID)).isNull();
    }
}
