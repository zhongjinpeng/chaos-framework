package com.michael.chaos.audit;

import com.michael.chaos.core.metrics.ChaosMeterNames;
import com.michael.chaos.core.metrics.ChaosMetrics;
import com.michael.chaos.core.metrics.NoopChaosMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步审计事件发布器。
 *
 * <p>审计事件此前一律在请求线程内同步发布。配合 {@code chaos-audit-jdbc} 时，每次登录、每次权限拒绝
 * 都是一次 inline 数据库写入：扫描器批量打未授权接口、或登录风暴时，既放大了请求延迟，
 * 也把审计写入变成了可被外部触发的放大点。</p>
 *
 * <p>本实现把发布放到单独的守护线程上，请求线程只做一次入队。</p>
 *
 * <p><b>有界队列且满时丢弃</b>：审计重要，但不能重要到让业务请求排队等数据库。队列满说明下游已经跟不上，
 * 此时阻塞业务只会把下游故障放大成全站故障。丢弃的事件计入 {@code chaos.audit.events{outcome="dropped"}}
 * 并按固定间隔打告警日志，运维据此扩容或降级，而不是靠请求超时来发现。合规场景要求零丢失时，
 * 应把 {@code chaos.audit.async.enabled} 关掉走同步写入，或换成 MQ 实现。</p>
 */
public class AsyncAuditEventPublisher implements AuditEventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditEventPublisher.class);

    private static final Duration DROP_LOG_INTERVAL = Duration.ofMinutes(1);

    /**
     * 停止时等待队列排空的时长上限。
     */
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final AuditEventPublisher delegate;

    private final BlockingQueue<AuditEvent> queue;

    private final ChaosMetrics metrics;

    private final Thread worker;

    private final AtomicLong dropped = new AtomicLong();

    private volatile long lastDropLogNanos = System.nanoTime() - DROP_LOG_INTERVAL.toNanos();

    private volatile boolean running = true;

    /**
     * 创建异步发布器并立即启动工作线程。
     *
     * @param delegate 真正执行写入的发布器
     * @param queueCapacity 队列容量
     * @param metrics 治理指标上报端口，可为 {@code null}
     */
    public AsyncAuditEventPublisher(AuditEventPublisher delegate, int queueCapacity, ChaosMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.queue = new ArrayBlockingQueue<>(Math.max(queueCapacity, 1));
        this.metrics = metrics == null ? NoopChaosMetrics.instance() : metrics;
        this.worker = new Thread(this::drainLoop, "chaos-audit-publisher");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /**
     * 入队；队列满时丢弃并计数，绝不阻塞调用线程。
     */
    @Override
    public void publish(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (!running || !queue.offer(event)) {
            recordDrop();
            return;
        }
        metrics.increment(ChaosMeterNames.AUDIT_EVENTS, ChaosMeterNames.TAG_OUTCOME, "queued");
    }

    /**
     * 返回当前排队条数（测试和诊断用）。
     */
    public int queueSize() {
        return queue.size();
    }

    /**
     * 返回累计丢弃条数（测试和诊断用）。
     */
    public long droppedCount() {
        return dropped.get();
    }

    /**
     * 停止工作线程，尽量把队列中剩余事件写完。
     */
    @Override
    public void close() {
        running = false;
        worker.interrupt();
        try {
            worker.join(SHUTDOWN_TIMEOUT.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        // 关闭期间仍在队列里的事件在当前线程补写一次，避免优雅停机丢掉最后一批审计。
        List<AuditEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        remaining.forEach(this::publishSafely);
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            try {
                AuditEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event != null) {
                    publishSafely(event);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 单条事件写入失败不能让工作线程退出，否则之后所有审计都静默消失。
     */
    private void publishSafely(AuditEvent event) {
        try {
            delegate.publish(event);
            metrics.increment(ChaosMeterNames.AUDIT_EVENTS, ChaosMeterNames.TAG_OUTCOME, "published");
        } catch (RuntimeException ex) {
            metrics.increment(ChaosMeterNames.AUDIT_EVENTS, ChaosMeterNames.TAG_OUTCOME, "failed");
            log.warn("Failed to publish audit event, action={}: {}", event.action(), ex.getMessage());
        }
    }

    private void recordDrop() {
        long total = dropped.incrementAndGet();
        metrics.increment(ChaosMeterNames.AUDIT_EVENTS, ChaosMeterNames.TAG_OUTCOME, "dropped");
        long now = System.nanoTime();
        if (now - lastDropLogNanos >= DROP_LOG_INTERVAL.toNanos()) {
            lastDropLogNanos = now;
            log.warn("Audit queue is full, {} events dropped so far; "
                    + "increase chaos.audit.async.queue-capacity or speed up the audit sink", total);
        }
    }
}
