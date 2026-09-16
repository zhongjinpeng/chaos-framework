package com.michael.chaos.service.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.trace.TraceContext;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Micrometer Context Propagation 桥接测试。
 */
class ChaosContextThreadLocalAccessorTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 通过 Micrometer ContextSnapshot 包装的任务应恢复 chaos 上下文，并在结束后还原执行线程上下文。
     */
    @Test
    void shouldPropagateChaosContextThroughMicrometerSnapshot() throws Exception {
        ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor(new ChaosContextThreadLocalAccessor());
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder().contextRegistry(registry).build();
        TraceContext.start("trace-reactor", "span-reactor", "tenant-a", "user-a", "svc");
        ContextSnapshot snapshot = factory.captureAll();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> afterTenant = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            snapshot.wrap(() -> tenant.set(RequestContext.tenantId())).run();
            afterTenant.set(RequestContext.tenantId());
        });
        worker.start();
        worker.join();

        assertThat(tenant).hasValue("tenant-a");
        assertThat(afterTenant).hasValue("");
    }
}
