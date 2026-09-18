package com.michael.chaos.core.lock;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DistributedLockExecuteTest {

    /**
     * 拿到锁时要正常返回业务结果，锁不能改变业务语义。
     */
    @Test
    void executeReturnsActionResultWhenLockAcquired() throws Exception {
        DistributedLock lock = new AlwaysAcquireLock();
        String result = lock.execute("key", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> "hello");
        assertEquals("hello", result);
    }

    /**
     * 拿不到锁必须显式失败，静默跳过会让调用方以为业务执行过了。
     */
    @Test
    void executeThrowsIllegalStateExceptionWhenLockNotAcquired() {
        DistributedLock lock = new NeverAcquireLock();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                lock.execute("key", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> "hello"));
        assertTrue(ex.getMessage().contains("key"));
    }

    /**
     * 业务抛异常也要释放锁，否则一次异常会把这个 key 锁死。
     */
    @Test
    void executeCallsUnlockEvenIfActionThrows() {
        AtomicBoolean unlocked = new AtomicBoolean(false);
        DistributedLock lock = new TrackingLock(unlocked);

        assertThrows(RuntimeException.class, () ->
                lock.execute("key", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> {
                    throw new RuntimeException("boom");
                }));

        assertTrue(unlocked.get(), "unlock should have been called even when action throws");
    }

    // --- Test implementations ---

    private static class AlwaysAcquireLock implements DistributedLock {
        @Override
        public Optional<LockHandle> tryLock(String key, Duration waitTime, Duration leaseTime) {
            return Optional.of(new NoOpLockHandle());
        }
    }

    private static class NeverAcquireLock implements DistributedLock {
        @Override
        public Optional<LockHandle> tryLock(String key, Duration waitTime, Duration leaseTime) {
            return Optional.empty();
        }
    }

    private static class TrackingLock implements DistributedLock {
        private final AtomicBoolean unlocked;

        TrackingLock(AtomicBoolean unlocked) {
            this.unlocked = unlocked;
        }

        @Override
        public Optional<LockHandle> tryLock(String key, Duration waitTime, Duration leaseTime) {
            return Optional.of(new LockHandle() {
                @Override
                public void unlock() {
                    unlocked.set(true);
                }

                @Override
                public boolean isHeldByCurrentThread() {
                    return true;
                }
            });
        }
    }

    private static class NoOpLockHandle implements LockHandle {
        @Override
        public void unlock() {}

        @Override
        public boolean isHeldByCurrentThread() {
            return true;
        }
    }
}
