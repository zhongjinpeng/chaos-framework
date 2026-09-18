package com.michael.chaos.core.idempotent.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryIdempotentRepositoryTest {

    /**
     * 首次请求必须放行，幂等仓储只拦重复请求。
     */
    @Test
    void firstCallWithKeyReturnsTrue() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        assertTrue(repo.saveIfAbsent("order-123", Duration.ofSeconds(10)));
    }

    /**
     * TTL 内重复的幂等键必须被拦住，这是幂等的核心语义。
     */
    @Test
    void secondCallWithSameKeyWithinTtlReturnsFalse() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        repo.saveIfAbsent("order-123", Duration.ofSeconds(10));
        assertFalse(repo.saveIfAbsent("order-123", Duration.ofSeconds(10)));
    }

    /**
     * TTL 过期后同一个键要能再次使用，否则键会被永久占用。
     */
    @Test
    void afterTtlExpiresSameKeyReturnsTrue() throws InterruptedException {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        assertTrue(repo.saveIfAbsent("order-123", Duration.ofMillis(50)));
        Thread.sleep(100);
        assertTrue(repo.saveIfAbsent("order-123", Duration.ofMillis(50)));
    }

    /**
     * 容量满时淘汰最早写入的 key，而不是拒绝所有新 key 造成拒绝服务。
     */
    @Test
    void whenCapacityReachedOldestKeyIsEvicted() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository(2);
        assertTrue(repo.saveIfAbsent("k1", Duration.ofMinutes(1)));
        assertTrue(repo.saveIfAbsent("k2", Duration.ofMinutes(1)));

        assertTrue(repo.saveIfAbsent("k3", Duration.ofMinutes(1)));

        assertEquals(2, repo.size());
        assertTrue(repo.saveIfAbsent("k1", Duration.ofMinutes(1)), "evicted key can be saved again");
        assertFalse(repo.saveIfAbsent("k3", Duration.ofMinutes(1)));
    }

    /**
     * 业务失败时要能主动释放键，避免一次失败把这个键锁死到 TTL 结束。
     */
    @Test
    void removeReleasesKey() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        repo.saveIfAbsent("order-9", Duration.ofMinutes(1));
        repo.remove("order-9");
        assertTrue(repo.saveIfAbsent("order-9", Duration.ofMinutes(1)));
    }

    /**
     * 不同键之间互不影响，防止实现把不同请求算成同一个。
     */
    @Test
    void differentKeysBothReturnTrue() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        assertTrue(repo.saveIfAbsent("order-1", Duration.ofSeconds(10)));
        assertTrue(repo.saveIfAbsent("order-2", Duration.ofSeconds(10)));
    }
}
