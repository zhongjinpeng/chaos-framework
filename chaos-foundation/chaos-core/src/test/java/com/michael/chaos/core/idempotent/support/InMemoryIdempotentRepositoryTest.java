package com.michael.chaos.core.idempotent.support;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryIdempotentRepositoryTest {

    @Test
    void firstCallWithKeyReturnsTrue() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        assertTrue(repo.saveIfAbsent("order-123", Duration.ofSeconds(10)));
    }

    @Test
    void secondCallWithSameKeyWithinTtlReturnsFalse() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        repo.saveIfAbsent("order-123", Duration.ofSeconds(10));
        assertFalse(repo.saveIfAbsent("order-123", Duration.ofSeconds(10)));
    }

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

    @Test
    void removeReleasesKey() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        repo.saveIfAbsent("order-9", Duration.ofMinutes(1));
        repo.remove("order-9");
        assertTrue(repo.saveIfAbsent("order-9", Duration.ofMinutes(1)));
    }

    @Test
    void differentKeysBothReturnTrue() {
        InMemoryIdempotentRepository repo = new InMemoryIdempotentRepository();
        assertTrue(repo.saveIfAbsent("order-1", Duration.ofSeconds(10)));
        assertTrue(repo.saveIfAbsent("order-2", Duration.ofSeconds(10)));
    }
}
