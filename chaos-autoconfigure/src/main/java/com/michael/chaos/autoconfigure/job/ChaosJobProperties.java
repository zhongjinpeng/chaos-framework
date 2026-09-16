package com.michael.chaos.autoconfigure.job;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 定时任务配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "chaos.job")
public class ChaosJobProperties {

    /**
     * 是否启用 chaos-job 自动装配。
     */
    private boolean enabled = true;

    /**
     * 是否开启 Spring {@code @EnableScheduling}。默认开启以兼容旧版本；应用自行管理调度（如 XXL-JOB）时可关闭。
     */
    private boolean schedulingEnabled = true;

    /**
     * 任务互斥锁配置。
     */
    private Lock lock = new Lock();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    public void setSchedulingEnabled(boolean schedulingEnabled) {
        this.schedulingEnabled = schedulingEnabled;
    }

    public Lock getLock() {
        return lock;
    }

    public void setLock(Lock lock) {
        this.lock = lock == null ? new Lock() : lock;
    }

    /**
     * 任务互斥锁配置。
     */
    public static class Lock {

        /**
         * 任务锁 key 前缀，最终 key 为 {@code key-prefix + jobName}（Redis 实现还会叠加 chaos.redis.key-prefix）。
         */
        private String keyPrefix = "chaos:job:";

        /**
         * 锁租约。为空时使用 Redisson watchdog 自动续期，任务执行多久锁就持有多久；
         * 显式设置时必须大于任务最长执行时间，否则锁到期后其他实例可能并发执行。
         */
        private Duration leaseTime;

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Duration getLeaseTime() {
            return leaseTime;
        }

        public void setLeaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
        }
    }
}
