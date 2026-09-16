package com.michael.chaos.authorization.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 内存登录失败限制器测试。
 */
class InMemoryLoginFailureLimiterTest {

    /**
     * 达到失败阈值后锁定，锁定期结束自动解锁。
     */
    @Test
    void shouldLockAfterMaxFailuresAndUnlockAfterDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryLoginFailureLimiter limiter = new InMemoryLoginFailureLimiter(3, Duration.ofMinutes(15), 100, clock);

        limiter.recordFailure("t:alice");
        limiter.recordFailure("t:alice");
        assertThat(limiter.isLocked("t:alice")).isFalse();
        limiter.recordFailure("t:alice");
        assertThat(limiter.isLocked("t:alice")).isTrue();

        clock.advance(Duration.ofMinutes(16));

        assertThat(limiter.isLocked("t:alice")).isFalse();
    }

    /**
     * 登录成功后清除失败计数。
     */
    @Test
    void shouldResetOnSuccess() {
        InMemoryLoginFailureLimiter limiter = new InMemoryLoginFailureLimiter(2, Duration.ofMinutes(15), 100);

        limiter.recordFailure("t:alice");
        limiter.reset("t:alice");
        limiter.recordFailure("t:alice");

        assertThat(limiter.isLocked("t:alice")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
