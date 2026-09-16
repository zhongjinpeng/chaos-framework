package com.michael.chaos.core.ratelimit;

/**
 * 限流上下文。
 *
 * @param key 限流 key
 * @param permitsPerSecond 每秒许可数
 */
public record RateLimitContext(String key, int permitsPerSecond) {
}
