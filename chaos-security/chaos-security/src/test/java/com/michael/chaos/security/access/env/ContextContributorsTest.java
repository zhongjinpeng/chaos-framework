package com.michael.chaos.security.access.env;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import com.michael.chaos.trace.log.MdcKeys;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 环境属性贡献者测试。
 */
class ContextContributorsTest {

    @AfterEach
    void clearContext() {
        RequestContext.clear();
        MDC.clear();
    }

    /**
     * 时间属性应来自注入的时钟，便于 ABAC 做办公时间限制。
     */
    @Test
    void timeContributorShouldUseInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T14:30:00Z"), ZoneId.of("UTC"));
        Map<String, Object> environment = new LinkedHashMap<>();

        new TimeContextContributor(clock).contribute(environment);

        assertThat(environment)
                .containsEntry("now", "2026-09-18T14:30:00Z")
                .containsEntry("date", "2026-09-18")
                .containsEntry("time", "14:30:00")
                .containsEntry("hour", 14)
                .containsEntry("dayOfWeek", "FRIDAY");
    }

    /**
     * 请求上下文与 MDC 中的信息应写入环境属性。
     */
    @Test
    void requestContextContributorShouldExposeContextAndMdc() {
        RequestContext.set(new RequestContextSnapshot("trace-1", "span-1", "tenant-a", "1001", "order-service"));
        MDC.put(MdcKeys.IP, "10.0.0.7");
        MDC.put(MdcKeys.URI, "/api/orders/1");
        Map<String, Object> environment = new LinkedHashMap<>();

        new RequestContextContributor().contribute(environment);

        assertThat(environment)
                .containsEntry("traceId", "trace-1")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("userId", "1001")
                .containsEntry("appName", "order-service")
                .containsEntry("clientIp", "10.0.0.7")
                .containsEntry("uri", "/api/orders/1");
    }

    /**
     * 没有请求上下文时不应写入空值。
     */
    @Test
    void requestContextContributorShouldSkipBlankValues() {
        RequestContext.set(new RequestContextSnapshot("", "", "", "", ""));
        Map<String, Object> environment = new LinkedHashMap<>();

        new RequestContextContributor().contribute(environment);

        assertThat(environment).isEmpty();
    }
}
