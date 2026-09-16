package com.michael.chaos.core.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * service 代码使用的分布式锁抽象。
 *
 * <p>该接口仅暴露锁语义。Redis、数据库或其他具体实现应位于基础设施模块。</p>
 */
public interface DistributedLock {

    /**
     * 尝试获取锁，成功返回锁句柄，失败返回空。
     *
     * @param key 锁标识
     * @param waitTime 最大等待时间
     * @param leaseTime 锁自动释放时间
     * @return 锁句柄，获取失败时为空
     */
    Optional<LockHandle> tryLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 在持有分布式锁期间执行业务逻辑。
     *
     * <p>注意：如果 action 执行时间超过 leaseTime，锁会被自动释放，
     * 此时其他节点可能获取到锁。调用方应确保 leaseTime 大于 action 的最大预期执行时间。</p>
     */
    default <T> T execute(String key, Duration waitTime, Duration leaseTime, Callable<T> action) throws Exception {
        LockHandle handle = tryLock(key, waitTime, leaseTime)
                .orElseThrow(() -> new IllegalStateException("Failed to acquire lock: " + key));
        try {
            return action.call();
        } finally {
            handle.unlock();
        }
    }
}
