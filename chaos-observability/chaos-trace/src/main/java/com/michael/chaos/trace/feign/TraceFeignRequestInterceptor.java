package com.michael.chaos.trace.feign;

import com.michael.chaos.trace.TraceHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * OpenFeign trace 请求头透传拦截器。
 *
 * <p>实现类放在 chaos-trace（feign-core 为可选依赖），自动装配模块只负责注册 Bean：
 * 这样非 Spring Boot 场景也能直接复用，且自动装配模块不再承载业务实现。</p>
 */
public class TraceFeignRequestInterceptor implements RequestInterceptor {

    /**
     * 将当前 trace 上下文写入 Feign 出站请求头。
     */
    @Override
    public void apply(RequestTemplate template) {
        TraceHeaders.outgoing().forEach(template::header);
    }
}
