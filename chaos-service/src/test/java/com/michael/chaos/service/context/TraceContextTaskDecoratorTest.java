package com.michael.chaos.service.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.trace.TraceContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 异步任务 trace 上下文装饰器测试。
 */
class TraceContextTaskDecoratorTest {

    /**
     * 每个用例结束后清理线程上下文。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    /**
     * 装饰器应在任务执行时恢复提交线程的 trace 上下文。
     */
    @Test
    void shouldPropagateTraceContextToDecoratedTask() {
        TraceContext.start("trace-task", "span-task", "tenant-a", "user-a", "service-a");
        TraceContextTaskDecorator decorator = new TraceContextTaskDecorator();
        AtomicReference<String> traceId = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> traceId.set(TraceContext.traceId()));
        TraceContext.clear();
        decorated.run();

        assertThat(traceId).hasValue("trace-task");
        assertThat(TraceContext.traceId()).isBlank();
    }
}
