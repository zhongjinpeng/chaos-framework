package com.michael.chaos.redis.delay;

import com.michael.chaos.redis.key.RedisKeyPrefix;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;

/**
 * 基于 Redisson 的轻量延迟队列模板。
 *
 * <p>注意：Redisson 3.4x 起 {@code RDelayedQueue} 已标记为 deprecated，官方替代 {@code RReliableQueue} 需要 Redisson PRO。
 * 该模板仅适合对可靠性要求不高的场景（例如订单超时提醒）；需要可靠投递时请使用 MQ 延迟消息或 outbox + 定时任务。
 * 延迟队列依赖客户端内部定时转移任务，只有调用过 {@link #offer} 的实例才会转移到期元素。</p>
 */
@SuppressWarnings("deprecation")
public class DelayQueueTemplate implements DisposableBean {

    private final RedissonClient redissonClient;

    private final RedisKeyPrefix keyPrefix;

    private final Map<String, RDelayedQueue<?>> queues = new ConcurrentHashMap<>();

    /**
     * 创建不带 key 前缀的延迟队列模板。
     */
    public DelayQueueTemplate(RedissonClient redissonClient) {
        this(redissonClient, RedisKeyPrefix.none());
    }

    /**
     * 创建带 key 前缀的延迟队列模板。
     */
    public DelayQueueTemplate(RedissonClient redissonClient, RedisKeyPrefix keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyPrefix = keyPrefix == null ? RedisKeyPrefix.none() : keyPrefix;
    }

    /**
     * 投递延迟元素。
     *
     * @param delay 延迟时间，不能为 {@code null} 或负数
     */
    @SuppressWarnings("unchecked")
    public <T> void offer(String queueName, T value, Duration delay) {
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be null or negative");
        }
        RDelayedQueue<T> delayedQueue = (RDelayedQueue<T>) queues.computeIfAbsent(queueName, name -> {
            RBlockingQueue<T> blockingQueue = redissonClient.getBlockingQueue(keyPrefix.apply(name));
            return redissonClient.getDelayedQueue(blockingQueue);
        });
        delayedQueue.offer(value, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 阻塞获取到期元素。
     */
    public <T> T take(String queueName) throws InterruptedException {
        return redissonClient.<T>getBlockingQueue(keyPrefix.apply(queueName)).take();
    }

    @Override
    public void destroy() {
        queues.values().forEach(RDelayedQueue::destroy);
        queues.clear();
    }
}
