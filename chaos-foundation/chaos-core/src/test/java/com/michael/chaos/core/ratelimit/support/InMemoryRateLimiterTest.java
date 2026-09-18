package com.michael.chaos.core.ratelimit.support;

import static org.junit.jupiter.api.Assertions.*;

import com.michael.chaos.core.ratelimit.RateLimitContext;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    /**
     * 配额内的请求必须全部放行，限流器不能宁可错杀。
     */
    @Test
    void allowsRequestsUpToPermitsPerSecond() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        RateLimitContext context = new RateLimitContext("user:1", 5);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(context), "Request " + (i + 1) + " should be allowed");
        }
    }

    /**
     * 超过配额必须拒绝，这是限流器存在的理由。
     */
    @Test
    void rejectsRequestsBeyondPermitsPerSecond() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        RateLimitContext context = new RateLimitContext("user:2", 3);

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire(context));
        }

        assertFalse(limiter.tryAcquire(context), "Request beyond limit should be rejected");
        assertFalse(limiter.tryAcquire(context), "Subsequent request should also be rejected");
    }

    /**
     * 窗口滑动后配额要恢复，否则一次突发会把后续正常流量一直挡住。
     */
    @Test
    void afterWindowSlides_permitsAreAvailableAgain() throws InterruptedException {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        RateLimitContext context = new RateLimitContext("user:3", 2);

        assertTrue(limiter.tryAcquire(context));
        assertTrue(limiter.tryAcquire(context));
        assertFalse(limiter.tryAcquire(context));

        // Wait for the sliding window to expire (1 second window + buffer)
        Thread.sleep(1100);

        assertTrue(limiter.tryAcquire(context), "After window slides, permits should be available again");
        assertTrue(limiter.tryAcquire(context), "Second permit should also be available");
    }

    /**
     * 不同维度的配额互相独立，一个接口被限不应该波及另一个。
     */
    @Test
    void differentKeysHaveIndependentLimits() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        RateLimitContext contextA = new RateLimitContext("key:A", 2);
        RateLimitContext contextB = new RateLimitContext("key:B", 2);

        assertTrue(limiter.tryAcquire(contextA));
        assertTrue(limiter.tryAcquire(contextA));
        assertFalse(limiter.tryAcquire(contextA), "Key A should be exhausted");

        // Key B should still have its own independent permits
        assertTrue(limiter.tryAcquire(contextB), "Key B should be independent of Key A");
        assertTrue(limiter.tryAcquire(contextB));
        assertFalse(limiter.tryAcquire(contextB), "Key B should now be exhausted");
    }

    /**
     * key 数达到上限时淘汰最久未访问的 key，新 key 仍然可以获得许可，避免伪造海量 key 造成全站 429。
     */
    @Test
    void whenMaxKeysReached_leastRecentlyUsedKeyIsEvicted() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(2);

        assertTrue(limiter.tryAcquire(new RateLimitContext("key:1", 1)));
        assertTrue(limiter.tryAcquire(new RateLimitContext("key:2", 1)));
        assertFalse(limiter.tryAcquire(new RateLimitContext("key:1", 1)), "key:1 is exhausted and recently used");

        assertTrue(limiter.tryAcquire(new RateLimitContext("key:3", 1)),
                "New key should still be allowed when maxKeys is reached");
        assertEquals(2, limiter.size());
        assertFalse(limiter.tryAcquire(new RateLimitContext("key:1", 1)), "recently used key must survive eviction");
        assertTrue(limiter.tryAcquire(new RateLimitContext("key:2", 1)), "evicted key starts a fresh window");
    }

    /**
     * 攻击者用海量不同 key 刷请求时内存不能被撑爆，否则正常用户会被一起拖垮。
     */
    @Test
    void attackerFloodingDistinctKeysCannotBlockNormalUser() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(100);
        for (int i = 0; i < 10_000; i++) {
            limiter.tryAcquire(new RateLimitContext("ip:forged-" + i, 5));
        }

        assertTrue(limiter.tryAcquire(new RateLimitContext("ip:real-user", 5)));
    }
}
