package com.michael.chaos.audit;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.trace.TraceContext;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 审计事件构建辅助方法。
 *
 * <p>该类只服务审计领域，集中读取请求上下文和 MDC，避免各模块重复拼装审计公共字段。</p>
 */
public final class AuditSupport {

    private AuditSupport() {
    }

    /**
     * 使用当前上下文创建审计事件构造器。
     */
    public static AuditEvent.Builder event(String action, AuditOutcome outcome) {
        RequestContextSnapshot snapshot = RequestContext.current()
                .orElse(new RequestContextSnapshot("", "", "", "", ""));
        return AuditEvent.builder(action, outcome)
                .principalId(snapshot.userId())
                .tenantId(snapshot.tenantId())
                .traceId(resolveTraceId(snapshot))
                .ip(resolveMdc("ip"))
                .uri(resolveMdc("uri"));
    }

    /**
     * 创建不可变字符串属性 Map。
     */
    public static Map<String, String> attributes(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return Map.of();
        }
        return Map.of(key, value);
    }

    private static String resolveTraceId(RequestContextSnapshot snapshot) {
        String traceId = TraceContext.traceId();
        if (traceId == null || traceId.isBlank()) {
            return snapshot.traceId();
        }
        return traceId;
    }

    private static String resolveMdc(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }
}
