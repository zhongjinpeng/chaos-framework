package com.michael.chaos.mq.reliable;

import java.time.Duration;

/**
 * 固定间隔重试退避策略。
 */
public class FixedRetryBackoffStrategy implements RetryBackoffStrategy {

    private final Duration interval;

    /**
     * 创建固定间隔退避策略。
     */
    public FixedRetryBackoffStrategy(Duration interval) {
        this.interval = interval == null || interval.isNegative() || interval.isZero()
                ? Duration.ofSeconds(5)
                : interval;
    }

    /**
     * 返回固定等待时间。
     */
    @Override
    public Duration nextBackoff(int retryTimes) {
        return interval;
    }
}
