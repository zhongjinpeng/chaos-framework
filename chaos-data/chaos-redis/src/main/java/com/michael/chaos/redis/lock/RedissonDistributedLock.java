package com.michael.chaos.redis.lock;

import com.michael.chaos.core.lock.DistributedLock;
import com.michael.chaos.core.lock.LockHandle;
import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Redisson 的分布式锁。
 *
 * <p>{@code leaseTime} 为 {@code null}、0 或负数时不设置固定租约，启用 Redisson watchdog 自动续期，
 * 适合执行时间不可预估的任务。显式传入租约时 watchdog 关闭，任务超过租约后锁会被自动释放，
 * 此时释放锁会输出告警，提醒调用方临界区已经失去互斥。</p>
 */
public class RedissonDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedLock.class);

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    /**
     * 创建不带 key 前缀的分布式锁。
     */
    public RedissonDistributedLock(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带 key 前缀的分布式锁。
     */
    public RedissonDistributedLock(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 尝试获取锁。
     *
     * @param key 锁标识
     * @param waitTime 最大等待时间，{@code null} 表示不等待
     * @param leaseTime 租约时间，{@code null}/非正数表示启用 watchdog 自动续期
     */
    @Override
    public Optional<LockHandle> tryLock(String key, Duration waitTime, Duration leaseTime) {
        String lockKey = keyPrefix.apply(key);
        RLock lock = redissonClient.getLock(lockKey);
        long waitMillis = waitTime == null || waitTime.isNegative() ? 0 : waitTime.toMillis();
        // Redisson 约定 leaseTime = -1 时启用 watchdog。
        long leaseMillis = leaseTime == null || leaseTime.isNegative() || leaseTime.isZero() ? -1 : leaseTime.toMillis();
        try {
            boolean acquired = lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
            if (acquired) {
                return Optional.of(new RedissonLockHandle(lock, lockKey));
            }
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * 持锁执行业务逻辑；业务结束时如果锁已因租约到期被释放，输出 ERROR 日志。
     */
    @Override
    public <T> T execute(String key, Duration waitTime, Duration leaseTime, Callable<T> action) throws Exception {
        LockHandle handle = tryLock(key, waitTime, leaseTime)
                .orElseThrow(() -> new IllegalStateException("Failed to acquire lock: " + key));
        try {
            return action.call();
        } finally {
            if (!handle.isHeldByCurrentThread()) {
                log.error("Distributed lock expired before action completed, mutual exclusion may be broken, key={}, "
                        + "leaseTime={}", key, leaseTime);
            }
            handle.unlock();
        }
    }

    private static final class RedissonLockHandle implements LockHandle {

        private final RLock lock;

        private final String lockKey;

        private boolean released;

        private RedissonLockHandle(RLock lock, String lockKey) {
            this.lock = lock;
            this.lockKey = lockKey;
        }

        /**
         * 释放锁；重复调用静默忽略，锁已因租约过期被释放时只告警，不抛出异常。
         */
        @Override
        public void unlock() {
            if (released) {
                return;
            }
            released = true;
            if (!lock.isHeldByCurrentThread()) {
                log.warn("Distributed lock is no longer held by current thread (lease expired or already released), key={}",
                        lockKey);
                return;
            }
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException ex) {
                // isHeld 与 unlock 之间租约恰好到期。
                log.warn("Distributed lock expired during unlock, key={}", lockKey);
            }
        }

        @Override
        public boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }
    }
}
