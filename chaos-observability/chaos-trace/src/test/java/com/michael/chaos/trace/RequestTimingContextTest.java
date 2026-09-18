package com.michael.chaos.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.core.context.ContextSnapshot;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RequestTimingContextTest {

    /**
     * 同名阶段嵌套时只算一次，避免重试或嵌套调用把耗时重复累加。
     */
    @Test
    void accumulatesStagesAndCollapsesNestedSameStage() {
        RequestTiming timing = RequestTiming.start();

        try (RequestTimingContext.Scope ignored = RequestTimingContext.open(timing);
                RequestTimingContext.StageScope outer = RequestTimingContext.stage("PostgreSQL");
                RequestTimingContext.StageScope inner = RequestTimingContext.stage("PostgreSQL")) {
            assertThat(RequestTimingContext.current()).containsSame(timing);
        }

        RequestTiming.Snapshot snapshot = timing.snapshot();
        assertThat(snapshot.stages()).containsOnlyKeys("postgresql");
        assertThat(snapshot.stages().get("postgresql").count()).isOne();
        assertThat(snapshot.stages().get("postgresql").totalNanos()).isPositive();
        assertThat(RequestTimingContext.current()).isEmpty();
    }

    /**
     * 异步切换线程后要继续累加到同一个计时器，否则异步部分的耗时会丢失。
     */
    @Test
    void propagatesTheSameAccumulatorAcrossFrameworkContextSnapshot() throws Exception {
        RequestTiming timing = RequestTiming.start();
        ContextSnapshot snapshot;
        try (RequestTimingContext.Scope ignored = RequestTimingContext.open(timing)) {
            snapshot = ContextPropagation.capture();
        }

        CountDownLatch completed = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try (ContextAccessor.Scope ignored = ContextPropagation.restore(snapshot);
                    RequestTimingContext.StageScope stage = RequestTimingContext.stage("grpc")) {
                assertThat(RequestTimingContext.current()).containsSame(timing);
            } finally {
                completed.countDown();
            }
        });
        worker.start();

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(timing.snapshot().stages()).containsKey("grpc");
    }
}
