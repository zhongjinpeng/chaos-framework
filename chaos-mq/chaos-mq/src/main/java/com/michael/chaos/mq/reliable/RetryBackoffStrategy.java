package com.michael.chaos.mq.reliable;

import java.time.Duration;

/**
 * 可靠消息重试退避策略。
 */
@FunctionalInterface
public interface RetryBackoffStrategy {

    /**
     * 计算下一次重试等待时间。
     *
     * @param retryTimes 即将进入的重试次数，从 1 开始
     * @return 等待时间
     */
    Duration nextBackoff(int retryTimes);
}
