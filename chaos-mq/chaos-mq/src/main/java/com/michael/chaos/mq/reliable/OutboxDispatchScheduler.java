package com.michael.chaos.mq.reliable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * outbox 定时派发与清理调度器。
 *
 * <p>使用独立的单线程调度器，而不是依赖 {@code @EnableScheduling}：引入 MQ starter 的应用未必开启了 Spring 调度，
 * 旧版本就是因为没人调用 {@link ReliableMessageDispatcher#dispatchDueMessages(int)}，消息永远停在 PENDING。</p>
 *
 * <p>多实例部署时每个实例都会运行调度器，依赖仓储的原子 claim 保证同一消息只被一个实例发送。
 * 一轮派发满批时立即进入下一轮，避免积压时仍按固定间隔慢慢消化。</p>
 */
public class OutboxDispatchScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchScheduler.class);

    private final ReliableMessageDispatcher dispatcher;

    private final Settings settings;

    private ScheduledExecutorService executor;

    /**
     * 创建调度器。
     */
    public OutboxDispatchScheduler(ReliableMessageDispatcher dispatcher, Settings settings) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * 启动派发和清理任务。
     */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chaos-outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::dispatchSafely,
                settings.initialDelay().toMillis(), settings.interval().toMillis(), TimeUnit.MILLISECONDS);
        if (settings.cleanupEnabled()) {
            executor.scheduleWithFixedDelay(this::cleanupSafely,
                    settings.cleanupInterval().toMillis(), settings.cleanupInterval().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 停止调度，等待当前批次结束。
     */
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    /**
     * 调度器是否运行中。
     */
    public synchronized boolean isRunning() {
        return executor != null;
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * 执行一轮派发；满批时继续派发，最多连续 {@code maxRoundsPerTick} 轮。
     */
    void dispatchSafely() {
        try {
            int rounds = 0;
            while (dispatcher.dispatchDueMessages(settings.batchSize()) >= settings.batchSize()
                    && ++rounds < settings.maxRoundsPerTick()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            // 表不存在、数据库不可用等异常不能终止调度线程，否则恢复后也不会再派发。
            log.warn("Outbox dispatch round failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 执行一次已发送消息清理。
     */
    void cleanupSafely() {
        try {
            int deleted;
            int rounds = 0;
            do {
                deleted = dispatcher.purgeSentMessages(settings.retention(), settings.cleanupBatchSize());
            } while (deleted >= settings.cleanupBatchSize() && ++rounds < settings.maxRoundsPerTick());
        } catch (RuntimeException ex) {
            log.warn("Outbox cleanup failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 调度参数。
     *
     * @param initialDelay 启动后首次派发延迟
     * @param interval 两轮派发之间的间隔
     * @param batchSize 每轮抢占条数
     * @param maxRoundsPerTick 满批时单次调度最多连续派发的轮数
     * @param cleanupEnabled 是否清理已发送消息
     * @param cleanupInterval 清理间隔
     * @param retention 已发送消息保留时长
     * @param cleanupBatchSize 每批删除条数
     */
    public record Settings(
            Duration initialDelay,
            Duration interval,
            int batchSize,
            int maxRoundsPerTick,
            boolean cleanupEnabled,
            Duration cleanupInterval,
            Duration retention,
            int cleanupBatchSize
    ) {

        /**
         * 规范化调度参数。
         */
        public Settings {
            initialDelay = nonNegative(initialDelay, Duration.ofSeconds(10));
            interval = positive(interval, Duration.ofSeconds(1));
            batchSize = Math.max(batchSize, 1);
            maxRoundsPerTick = Math.max(maxRoundsPerTick, 1);
            cleanupInterval = positive(cleanupInterval, Duration.ofHours(1));
            retention = nonNegative(retention, Duration.ofDays(7));
            cleanupBatchSize = Math.max(cleanupBatchSize, 1);
        }

        private static Duration positive(Duration value, Duration defaultValue) {
            return value == null || value.isNegative() || value.isZero() ? defaultValue : value;
        }

        private static Duration nonNegative(Duration value, Duration defaultValue) {
            return value == null || value.isNegative() ? defaultValue : value;
        }
    }
}
