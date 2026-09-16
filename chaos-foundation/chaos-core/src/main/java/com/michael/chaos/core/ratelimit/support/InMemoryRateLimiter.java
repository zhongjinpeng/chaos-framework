package com.michael.chaos.core.ratelimit.support;

import com.michael.chaos.core.ratelimit.RateLimitContext;
import com.michael.chaos.core.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于滑动窗口计数的内存限流器。
 *
 * <p>每个 key 维护最近 1 秒内请求时间戳的环形队列，新请求到达时先清除过期时间戳，再判断是否超过许可数。</p>
 *
 * <p>容量策略：key 数达到上限时淘汰<b>最久未访问</b>的 key（LRU），而不是拒绝所有新 key。
 * 原实现在容量耗尽后对所有新 key 返回 false，攻击者只要伪造足够多的不同 IP 就能让正常用户全部 429。
 * LRU 淘汰最坏情况下只会让被淘汰 key 的计数重置，影响限流精度但不会造成全站拒绝服务。</p>
 *
 * <p>仅适用于开发和测试环境。生产环境应使用 Redis 限流实现。</p>
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final System.Logger log = System.getLogger(InMemoryRateLimiter.class.getName());
    private static final int DEFAULT_MAX_KEYS = 10_000;
    private static final long WINDOW_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long EVICTION_LOG_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();

    /**
     * accessOrder=true 的 LinkedHashMap 天然维护 LRU 顺序。所有访问都在对象锁内完成，
     * 单次操作 O(permits) 摊销，锁持有时间很短，满足开发和测试场景。
     */
    private final LinkedHashMap<String, SlidingWindow> windows;
    private final int maxKeys;
    private long lastEvictionLogNanos;

    public InMemoryRateLimiter() {
        this(DEFAULT_MAX_KEYS);
    }

    public InMemoryRateLimiter(int maxKeys) {
        this.maxKeys = Math.max(maxKeys, 1);
        this.windows = new LinkedHashMap<>(16, 0.75f, true);
        this.lastEvictionLogNanos = System.nanoTime() - EVICTION_LOG_INTERVAL_NANOS;
    }

    @Override
    public synchronized boolean tryAcquire(RateLimitContext context) {
        long now = System.nanoTime();
        int permits = Math.max(context.permitsPerSecond(), 0);
        SlidingWindow window = windows.get(context.key());
        if (window == null) {
            if (windows.size() >= maxKeys) {
                evictLeastRecentlyUsed(now);
            }
            window = new SlidingWindow();
            windows.put(context.key(), window);
        }
        return window.tryAcquire(now, permits);
    }

    /**
     * 返回当前保留的 key 数（测试和诊断用）。
     */
    public synchronized int size() {
        return windows.size();
    }

    private void evictLeastRecentlyUsed(long now) {
        Iterator<Map.Entry<String, SlidingWindow>> iterator = windows.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
        if (now - lastEvictionLogNanos >= EVICTION_LOG_INTERVAL_NANOS) {
            lastEvictionLogNanos = now;
            log.log(System.Logger.Level.WARNING,
                    "InMemoryRateLimiter reached maxKeys={0}, evicting least recently used keys", maxKeys);
        }
    }

    /**
     * 单个 key 的滑动窗口日志。调用方持有外层锁，无需并发容器。
     */
    private static final class SlidingWindow {

        private long[] timestamps = new long[4];
        private int head;
        private int size;

        private boolean tryAcquire(long nowNanos, int permitsPerSecond) {
            long windowStart = nowNanos - WINDOW_NANOS;
            while (size > 0 && timestamps[head] <= windowStart) {
                head = (head + 1) % timestamps.length;
                size--;
            }
            if (size >= permitsPerSecond) {
                return false;
            }
            if (size == timestamps.length) {
                grow();
            }
            timestamps[(head + size) % timestamps.length] = nowNanos;
            size++;
            return true;
        }

        private void grow() {
            long[] expanded = new long[timestamps.length * 2];
            for (int i = 0; i < size; i++) {
                expanded[i] = timestamps[(head + i) % timestamps.length];
            }
            timestamps = expanded;
            head = 0;
        }
    }
}
