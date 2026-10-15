package com.chaos.gateway.filter;

import com.chaos.trace.log.MdcKeys;
import com.chaos.trace.log.MdcSupport;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 将认证成功后的 Gateway exchange 身份同步到当前日志 MDC。
 *
 * <p>认证过滤器把 token 身份写入 exchange attributes；本过滤器在其后包裹下游链路，保证网关
 * 内部业务日志可以读取用户和租户，同时在响应结束后恢复 Reactor 工作线程原有 MDC。</p>
 */
public class GatewayIdentityMdcFilter implements GlobalFilter, Ordered {

    /**
     * 在内置认证过滤器之后执行，在路由转发之前建立日志上下文。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = GatewayExchangeAttributes.authenticatedUserId(exchange);
        String tenantId = GatewayExchangeAttributes.authenticatedTenantId(exchange);
        if (userId.isBlank() && tenantId.isBlank()) {
            return chain.filter(exchange);
        }
        Map<String, String> previousMdc = MdcSupport.copy();
        MdcSupport.putIfNotBlank(MdcKeys.USER_ID, userId);
        MdcSupport.putIfNotBlank(MdcKeys.TENANT_ID, tenantId);
        return chain.filter(exchange).doFinally(signal -> MdcSupport.restore(previousMdc));
    }

    /**
     * 返回过滤器执行顺序。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 31;
    }
}
