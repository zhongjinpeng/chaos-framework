package com.michael.chaos.trace.monitor;

import com.michael.chaos.core.context.RequestContext;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * Micrometer Observation 统一上下文过滤器。
 *
 * <p>该过滤器只追加低基数字段，便于 Prometheus、OpenTelemetry 和 SkyWalking
 * 侧按 framework、tenant 维度关联观测数据。高基数 traceId 不写入指标标签，避免指标膨胀。</p>
 */
public class ChaosObservationFilter implements ObservationFilter {

    private final boolean includeTenantTag;

    public ChaosObservationFilter() {
        this(false);
    }

    public ChaosObservationFilter(boolean includeTenantTag) {
        this.includeTenantTag = includeTenantTag;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        context.addLowCardinalityKeyValue(KeyValue.of("framework", "chaos"));
        if (includeTenantTag) {
            String tenantId = RequestContext.tenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                context.addLowCardinalityKeyValue(KeyValue.of("tenant", tenantId));
            }
        }
        return context;
    }
}
