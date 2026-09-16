package com.michael.chaos.autoconfigure.mq;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MQ 自动装配配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.mq")
public class ChaosMqProperties {

    /**
     * MQ 发布器配置。
     */
    @Valid
    @NotNull(message = "chaos.mq.publisher must not be null")
    private Publisher publisher = new Publisher();

    /**
     * JDBC outbox 配置。
     */
    @Valid
    @NotNull(message = "chaos.mq.outbox must not be null")
    private Outbox outbox = new Outbox();

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher == null ? new Publisher() : publisher;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox == null ? new Outbox() : outbox;
    }

    /**
     * MQ 发布器配置。
     */
    public static class Publisher {

        /**
         * 等待 broker 确认的超时时间。超时视为发送失败，由 outbox 重试。
         */
        @NotNull(message = "chaos.mq.publisher.send-timeout must not be null")
        private Duration sendTimeout = Duration.ofSeconds(10);

        public Duration getSendTimeout() {
            return sendTimeout;
        }

        public void setSendTimeout(Duration sendTimeout) {
            this.sendTimeout = sendTimeout;
        }
    }

    /**
     * outbox 配置。
     */
    public static class Outbox {

        /**
         * 是否启用 JDBC outbox 自动装配。
         */
        private boolean enabled = true;

        /**
         * outbox 表名，只允许普通标识符（可带 schema 前缀）。
         */
        @NotBlank(message = "chaos.mq.outbox.table-name must not be blank")
        private String tableName = "chaos_mq_outbox";

        /**
         * SENDING 状态超过该时间后允许其他实例重新抢占派发，应明显大于 send-timeout。
         */
        @NotNull(message = "chaos.mq.outbox.claim-timeout must not be null")
        private Duration claimTimeout = Duration.ofMinutes(5);

        /**
         * 派发器配置。
         */
        @Valid
        @NotNull(message = "chaos.mq.outbox.dispatcher must not be null")
        private Dispatcher dispatcher = new Dispatcher();

        /**
         * 已发送消息清理配置。
         */
        @Valid
        @NotNull(message = "chaos.mq.outbox.cleanup must not be null")
        private Cleanup cleanup = new Cleanup();

        /**
         * 积压健康检查配置。
         */
        @Valid
        @NotNull(message = "chaos.mq.outbox.health must not be null")
        private Health health = new Health();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Duration getClaimTimeout() {
            return claimTimeout;
        }

        public void setClaimTimeout(Duration claimTimeout) {
            this.claimTimeout = claimTimeout;
        }

        public Dispatcher getDispatcher() {
            return dispatcher;
        }

        public void setDispatcher(Dispatcher dispatcher) {
            this.dispatcher = dispatcher == null ? new Dispatcher() : dispatcher;
        }

        public Cleanup getCleanup() {
            return cleanup;
        }

        public void setCleanup(Cleanup cleanup) {
            this.cleanup = cleanup == null ? new Cleanup() : cleanup;
        }

        public Health getHealth() {
            return health;
        }

        public void setHealth(Health health) {
            this.health = health == null ? new Health() : health;
        }
    }

    /**
     * outbox 派发器配置。
     */
    public static class Dispatcher {

        /**
         * 是否自动运行派发器。需要同时存在 outbox 仓储和真实 MessagePublisher（Kafka/RocketMQ/自定义）。
         */
        private boolean enabled = true;

        /**
         * 应用启动后首次派发延迟。
         */
        @NotNull(message = "chaos.mq.outbox.dispatcher.initial-delay must not be null")
        private Duration initialDelay = Duration.ofSeconds(10);

        /**
         * 两轮派发之间的间隔。
         */
        @NotNull(message = "chaos.mq.outbox.dispatcher.interval must not be null")
        private Duration interval = Duration.ofSeconds(1);

        /**
         * 每轮抢占的消息条数。
         */
        @Min(value = 1, message = "chaos.mq.outbox.dispatcher.batch-size must be greater than 0")
        private int batchSize = 100;

        /**
         * 最大重试次数，超过后进入死信。
         */
        @Min(value = 0, message = "chaos.mq.outbox.dispatcher.max-retry-times must not be negative")
        private int maxRetryTimes = 10;

        /**
         * 固定重试间隔。
         */
        @NotNull(message = "chaos.mq.outbox.dispatcher.retry-backoff must not be null")
        private Duration retryBackoff = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetryTimes() {
            return maxRetryTimes;
        }

        public void setMaxRetryTimes(int maxRetryTimes) {
            this.maxRetryTimes = maxRetryTimes;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }

    /**
     * 已发送消息清理配置。
     */
    /**
     * outbox 积压健康检查配置。
     *
     * <p>派发器卡住时消息会不断堆积，但此前 {@code /actuator/health} 依然是 UP，
     * 滚动发布和扩缩容都会照常放行。超过阈值时健康检查转为 DOWN。</p>
     */
    public static class Health {

        /**
         * 是否注册 outbox 健康指示器。
         */
        private boolean enabled = true;

        /**
         * 待派发条数告警阈值，超过则判定为不健康。
         */
        @Min(value = 1, message = "chaos.mq.outbox.health.pending-threshold must be positive")
        private long pendingThreshold = 10_000L;

        /**
         * 死信条数告警阈值，超过则判定为不健康。
         */
        @Min(value = 1, message = "chaos.mq.outbox.health.dead-letter-threshold must be positive")
        private long deadLetterThreshold = 100L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPendingThreshold() {
            return pendingThreshold;
        }

        public void setPendingThreshold(long pendingThreshold) {
            this.pendingThreshold = pendingThreshold;
        }

        public long getDeadLetterThreshold() {
            return deadLetterThreshold;
        }

        public void setDeadLetterThreshold(long deadLetterThreshold) {
            this.deadLetterThreshold = deadLetterThreshold;
        }
    }

    public static class Cleanup {

        /**
         * 是否定期删除已发送消息，避免 outbox 表无限增长。
         */
        private boolean enabled = true;

        /**
         * 已发送消息保留时长。
         */
        @NotNull(message = "chaos.mq.outbox.cleanup.retention must not be null")
        private Duration retention = Duration.ofDays(7);

        /**
         * 清理间隔。
         */
        @NotNull(message = "chaos.mq.outbox.cleanup.interval must not be null")
        private Duration interval = Duration.ofHours(1);

        /**
         * 每批删除条数。
         */
        @Min(value = 1, message = "chaos.mq.outbox.cleanup.batch-size must be greater than 0")
        private int batchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
