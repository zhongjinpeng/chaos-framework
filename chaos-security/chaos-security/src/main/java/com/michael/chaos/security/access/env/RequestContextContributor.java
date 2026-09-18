package com.michael.chaos.security.access.env;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.security.api.access.AccessEnvironment;
import com.michael.chaos.security.api.access.AuthorizationContextContributor;
import com.michael.chaos.trace.log.MdcKeys;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 请求上下文环境属性贡献者。
 *
 * <p>写入 {@code traceId}、{@code tenantId}、{@code userId}、{@code appName}（来自 {@link RequestContext}）
 * 以及 {@code clientIp}、{@code uri}（来自 MDC，由 chaos-web 的 TraceFilter 按可信代理规则写入）。</p>
 *
 * <p>客户端 IP 复用 MDC 而不是在这里重新解析转发头，避免可信代理配置出现第二份、两处解析结果不一致。</p>
 */
public class RequestContextContributor implements AuthorizationContextContributor {

    @Override
    public void contribute(Map<String, Object> environment) {
        RequestContext.current().ifPresent(snapshot -> contribute(environment, snapshot));
        putIfNotBlank(environment, AccessEnvironment.CLIENT_IP, MDC.get(MdcKeys.IP));
        putIfNotBlank(environment, AccessEnvironment.URI, MDC.get(MdcKeys.URI));
    }

    private void contribute(Map<String, Object> environment, RequestContextSnapshot snapshot) {
        putIfNotBlank(environment, AccessEnvironment.TRACE_ID, snapshot.traceId());
        putIfNotBlank(environment, AccessEnvironment.TENANT_ID, snapshot.tenantId());
        putIfNotBlank(environment, AccessEnvironment.USER_ID, snapshot.userId());
        putIfNotBlank(environment, AccessEnvironment.APP_NAME, snapshot.appName());
    }

    private void putIfNotBlank(Map<String, Object> environment, String key, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(key, value);
        }
    }
}
