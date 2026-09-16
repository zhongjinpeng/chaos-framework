package com.michael.chaos.mybatis.audit;

import com.michael.chaos.core.context.RequestContext;

/**
 * 从 {@link RequestContext} 读取操作人的默认实现，用于未引入 chaos-security 的服务。
 *
 * <p>RequestContext 中的用户 ID 应由认证结果或可信网关写入；不要在未经认证的入口直接信任客户端请求头。</p>
 */
public class RequestContextAuditorProvider implements AuditorProvider {

    @Override
    public String currentAuditor() {
        return RequestContext.userId();
    }
}
