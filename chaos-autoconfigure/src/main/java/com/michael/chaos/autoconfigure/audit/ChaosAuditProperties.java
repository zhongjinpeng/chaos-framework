package com.michael.chaos.autoconfigure.audit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 审计模块配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.audit")
public class ChaosAuditProperties {

    /**
     * 异步发布配置。
     */
    @Valid
    @NotNull(message = "chaos.audit.async must not be null")
    private Async async = new Async();

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async == null ? new Async() : async;
    }

    /**
     * 异步审计发布配置。
     */
    public static class Async {

        /**
         * 是否异步发布审计事件。
         *
         * <p>默认关闭，保持同步写入的既有行为。<b>配合 {@code chaos-audit-jdbc} 时强烈建议开启</b>：
         * 同步模式下每次登录、每次权限拒绝都是请求线程内的一次数据库写入，扫描器批量打未授权接口
         * 会把审计写入变成可被外部触发的放大点。</p>
         */
        private boolean enabled = false;

        /**
         * 待发布事件队列容量。
         *
         * <p>队列满时丢弃新事件并计入 {@code chaos.audit.events{outcome="dropped"}}，绝不阻塞业务线程：
         * 队列满说明下游已经跟不上，此时阻塞业务只会把下游故障放大成全站故障。
         * 合规要求零丢失时应关闭异步，或改用 MQ 实现。</p>
         */
        @Min(value = 1, message = "chaos.audit.async.queue-capacity must be positive")
        private int queueCapacity = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
