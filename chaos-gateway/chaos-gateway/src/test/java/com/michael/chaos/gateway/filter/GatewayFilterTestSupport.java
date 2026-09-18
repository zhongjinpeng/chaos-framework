package com.michael.chaos.gateway.filter;

import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * 网关过滤器测试公共断言辅助。
 *
 * <p>只放"每个过滤器测试都要用、且实现完全一致"的读取逻辑；各测试自己的 exchange 构造方式参数不同，
 * 仍由各测试维护，硬凑成一个通用方法反而更难读。</p>
 */
final class GatewayFilterTestSupport {

    private GatewayFilterTestSupport() {
    }

    /**
     * 读取已写入的响应体；过滤器未写响应时返回 {@code null}。
     */
    static String responseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
