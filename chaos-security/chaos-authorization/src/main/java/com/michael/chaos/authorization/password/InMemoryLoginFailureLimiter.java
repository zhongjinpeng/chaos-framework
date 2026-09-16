package com.michael.chaos.authorization.password;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于本地内存的登录失败次数限制实现。
 *
 * <p>仅适合单实例或开发环境；多实例部署时每个实例单独计数，实际可尝试次数会放大为实例数倍。</p>
 */
public class InMemoryLoginFailureLimiter implements LoginFailureLimiter {

    private final int maxFailures;

    private final Duration lockDuration;

    private final int maxEntries;

    private final Clock clock;

    private final Map<String, FailureCounter> counters = new ConcurrentHashMap<>();

    /**
     * 创建内存登录失败限制器。
     *
     * @param maxFailures 锁定前允许的失败次数
     * @param lockDuration 锁定时长和计数窗口
     * @param maxEntries 最多保留的计数条目
     */
    public InMemoryLoginFailureLimiter(int maxFailures, Duration lockDuration, int maxEntries) {
        this(maxFailures, lockDuration, maxEntries, Clock.systemUTC());
    }

    InMemoryLoginFailureLimiter(int maxFailures, Duration lockDuration, int maxEntries, Clock clock) {
        this.maxFailures = Math.max(maxFailures, 1);
        this.lockDuration = lockDuration;
        this.maxEntries = Math.max(maxEntries, 1);
        this.clock = clock;
    }

    @Override
    public boolean isLocked(String key) {
        FailureCounter counter = counters.get(key);
        if (counter == null) {
            return false;
        }
        if (counter.expired(clock.instant())) {
            counters.remove(key, counter);
            return false;
        }
        return counter.failures() >= maxFailures;
    }

    @Override
    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (counters.size() >= maxEntries && !counters.containsKey(key)) {
            counters.entrySet().removeIf(entry -> entry.getValue().expired(now));
        }
        counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired(now)) {
                return new FailureCounter(1, now.plus(lockDuration));
            }
            return new FailureCounter(existing.failures() + 1, now.plus(lockDuration));
        });
    }

    @Override
    public void reset(String key) {
        counters.remove(key);
    }

    private record FailureCounter(int failures, Instant expiresAt) {

        private boolean expired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
