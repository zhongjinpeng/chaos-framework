package com.michael.chaos.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 异步审计发布器测试。
 */
class AsyncAuditEventPublisherTest {

    private static AuditEvent event(String reason) {
        return AuditSupport.event(AuditAction.SECURITY_PERMISSION_DENIED, AuditOutcome.DENIED)
                .reason(reason)
                .build();
    }

    private static final class RecordingMetrics implements ChaosMetrics {

        private final List<String> tags = new CopyOnWriteArrayList<>();

        @Override
        public void increment(String name, String... values) {
            tags.add(name + ":" + String.join(",", values));
        }
    }

    /**
     * 事件最终会被委托发布器写出。
     */
    @Test
    void shouldPublishAsynchronously() {
        List<AuditEvent> published = new CopyOnWriteArrayList<>();
        try (AsyncAuditEventPublisher publisher =
                     new AsyncAuditEventPublisher(published::add, 100, null)) {
            publisher.publish(event("first"));
            publisher.publish(event("second"));

            await().atMost(Duration.ofSeconds(2)).until(() -> published.size() == 2);
        }
    }

    /**
     * 调用线程不得被下游阻塞：委托发布器卡住时 publish 必须立刻返回。
     *
     * <p>这是引入异步的核心动机——同步模式下扫描器批量打未授权接口，
     * 每个请求都要等一次数据库写入。</p>
     */
    @Test
    void shouldNotBlockCallerWhenDelegateIsSlow() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AuditEventPublisher blocking = auditEvent -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        // 必须在 close() 之前放行：close 会等待工作线程，阻塞的委托会把关闭拖满超时。
        try (AsyncAuditEventPublisher publisher = new AsyncAuditEventPublisher(blocking, 10, null)) {
            long start = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                publisher.publish(event("e" + i));
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(elapsedMs).as("publish 不应等待下游").isLessThan(500L);
            release.countDown();
        }
    }

    /**
     * 队列满时丢弃并计数，绝不阻塞：队列满说明下游跟不上，此时阻塞业务会把下游故障放大成全站故障。
     */
    @Test
    void shouldDropAndCountWhenQueueIsFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AuditEventPublisher blocking = auditEvent -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        RecordingMetrics metrics = new RecordingMetrics();
        try (AsyncAuditEventPublisher publisher = new AsyncAuditEventPublisher(blocking, 1, metrics)) {
            for (int i = 0; i < 50; i++) {
                publisher.publish(event("e" + i));
            }

            assertThat(publisher.droppedCount()).isPositive();
            assertThat(metrics.tags)
                    .anyMatch(tag -> tag.startsWith(ChaosMeterNames.AUDIT_EVENTS) && tag.endsWith("dropped"));
            release.countDown();
        }
    }

    /**
     * 单条事件写入失败不能让工作线程退出，否则之后所有审计都静默消失。
     */
    @Test
    void shouldKeepRunningAfterDelegateFailure() {
        List<AuditEvent> published = new CopyOnWriteArrayList<>();
        AuditEventPublisher flaky = auditEvent -> {
            if ("boom".equals(auditEvent.reason())) {
                throw new IllegalStateException("sink down");
            }
            published.add(auditEvent);
        };
        try (AsyncAuditEventPublisher publisher = new AsyncAuditEventPublisher(flaky, 100, null)) {
            publisher.publish(event("boom"));
            publisher.publish(event("ok"));

            await().atMost(Duration.ofSeconds(2)).until(() -> published.size() == 1);
        }
    }

    /**
     * 关闭时把队列里剩余的事件补写完，避免优雅停机丢掉最后一批审计。
     */
    @Test
    void shouldDrainRemainingEventsOnClose() {
        List<AuditEvent> published = new CopyOnWriteArrayList<>();
        AsyncAuditEventPublisher publisher = new AsyncAuditEventPublisher(published::add, 100, null);
        for (int i = 0; i < 20; i++) {
            publisher.publish(event("e" + i));
        }

        publisher.close();

        assertThat(published).hasSize(20);
    }
}
