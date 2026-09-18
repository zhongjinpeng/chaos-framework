package com.michael.chaos.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.trace.RequestTiming;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayAccessLogFilterTest {

    /**
     * 网关访问日志要覆盖整条过滤器链并单独聚合下游耗时，区分网关自己慢还是下游慢。
     */
    @Test
    void coversGatewayChainAndAggregatesDownstreamStage() {
        GatewayAccessLogFilter accessLogFilter = new GatewayAccessLogFilter("gateway", "test");
        GatewayDownstreamTimingFilter downstreamTimingFilter = new GatewayDownstreamTimingFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/quant/api").build());
        exchange.getAttributes().put(ChaosHeaders.TRACE_ID, "trace-1");

        accessLogFilter.filter(
                exchange,
                current -> downstreamTimingFilter.filter(current, ignored -> Mono.empty()))
                .block();

        RequestTiming timing = exchange.getAttribute(RequestTiming.ATTRIBUTE_NAME);
        assertThat(timing).isNotNull();
        assertThat(timing.snapshot().stages()).containsKey("downstream");
        assertThat(accessLogFilter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        assertThat(downstreamTimingFilter.getOrder()).isEqualTo(9_999);
    }
}
