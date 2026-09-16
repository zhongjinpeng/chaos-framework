package com.michael.chaos.core.lock;

/**
 * 分布式锁持有句柄。
 *
 * <p>通过 {@link DistributedLock#tryLock} 获取，调用 {@link #unlock()} 释放。
 * 实现 {@link AutoCloseable} 以支持 try-with-resources。</p>
 */
public interface LockHandle extends AutoCloseable {

    /**
     * 释放锁。重复调用安全（幂等）。
     */
    void unlock();

    /**
     * 当前线程是否仍持有该锁。
     */
    boolean isHeldByCurrentThread();

    @Override
    default void close() {
        unlock();
    }
}
