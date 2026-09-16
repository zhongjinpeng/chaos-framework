package com.michael.chaos.trace.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.michael.chaos.core.context.RequestContext;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Observation 统一过滤器测试。
 */
class ChaosObservationFilterTest {

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    /**
     * 默认只追加低基数的 framework 标签，不追加租户标签，避免指标基数膨胀。
     */
    @Test
    void shouldOnlyAddFrameworkTagByDefault() {
        RequestContext.setTenantId("tenant-a");
        Observation.Context context = new ChaosObservationFilter().map(new Observation.Context());

        assertEquals("chaos", value(context, "framework"));
        assertNull(context.getLowCardinalityKeyValue("tenant"));
    }

    /**
     * 显式开启后追加租户标签；租户值已在 RequestContext 入口经过白名单校验。
     */
    @Test
    void shouldAddTenantTagWhenEnabled() {
        RequestContext.setTenantId("tenant-a");
        Observation.Context context = new ChaosObservationFilter(true).map(new Observation.Context());

        assertEquals("tenant-a", value(context, "tenant"));
    }

    private static String value(Observation.Context context, String key) {
        KeyValue keyValue = context.getLowCardinalityKeyValue(key);
        return keyValue == null ? null : keyValue.getValue();
    }
}
