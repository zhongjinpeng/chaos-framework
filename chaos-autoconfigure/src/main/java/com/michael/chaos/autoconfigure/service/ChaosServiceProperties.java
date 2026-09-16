package com.michael.chaos.autoconfigure.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Service 层配置属性。
 *
 * <p>该模块刻意不声明 Bean Validation 约束：service starter 不强制引入校验实现，
 * 仅有 jakarta.validation API 而没有实现时 {@code @Validated} 绑定会直接失败。
 * 数值下限在 {@code SpringRetryExecutor} 中兜底修正，嵌套对象在 setter 中恢复默认值。</p>
 */
@Validated
@ConfigurationProperties(prefix = "chaos.service")
public class ChaosServiceProperties {

    /**
     * 默认重试配置。
     */
    private Retry retry = new Retry();

    /**
     * 上下文传播配置。
     */
    private ContextPropagation contextPropagation = new ContextPropagation();

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    public ContextPropagation getContextPropagation() {
        return contextPropagation;
    }

    public void setContextPropagation(ContextPropagation contextPropagation) {
        this.contextPropagation = contextPropagation == null ? new ContextPropagation() : contextPropagation;
    }

    /**
     * 默认 RetryExecutor 的重试配置。
     */
    public static class Retry {

        /**
         * 最大尝试次数（包含首次执行）。
         */
        private int maxAttempts = 3;

        /**
         * 固定退避间隔，单位毫秒。
         */
        private long backoffMs = 100;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }
    }

    /**
     * 上下文传播配置。
     */
    public static class ContextPropagation {

        /**
         * 是否把 chaos 上下文注册到 Micrometer Context Propagation（ContextRegistry）。
         *
         * <p>开启后 Reactor、CompletableFuture（配合 ContextExecutorService）等非 TaskExecutor 异步边界
         * 也能传播 trace、租户和用户上下文。默认关闭：chaos 恢复快照时会整体恢复 MDC，
         * 与 Micrometer Tracing 自带的 MDC 管理同时启用时需要先验证日志字段是否符合预期。</p>
         */
        private boolean micrometerEnabled = false;

        public boolean isMicrometerEnabled() {
            return micrometerEnabled;
        }

        public void setMicrometerEnabled(boolean micrometerEnabled) {
            this.micrometerEnabled = micrometerEnabled;
        }
    }
}
