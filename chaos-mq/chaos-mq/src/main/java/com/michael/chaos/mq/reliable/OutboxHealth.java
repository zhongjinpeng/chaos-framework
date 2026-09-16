package com.michael.chaos.mq.reliable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * outbox 积压健康判定。
 *
 * <p>派发器卡住（claim 持有者崩溃、下游 broker 长时间不可用、重试全部耗尽）此前只在日志里留痕：
 * 消息不断堆积，但 {@code /actuator/health} 依然是 UP，滚动发布和自动扩缩容都会照常放行。
 * 这里把"待派发条数"和"死信条数"变成可判定的健康状态。</p>
 *
 * <p>刻意不依赖 Spring Boot Actuator 类型：{@code chaos-mq} 不应为了一个健康检查强依赖 actuator，
 * 判定逻辑放在这里，{@code HealthIndicator} 适配交给 {@link OutboxHealthIndicator}（actuator 为 optional 依赖）。</p>
 */
public final class OutboxHealth {

    private final OutboxMessageRepository repository;

    private final long pendingThreshold;

    private final long deadLetterThreshold;

    /**
     * 创建健康判定。
     *
     * @param repository outbox 仓储
     * @param pendingThreshold 待派发条数告警阈值，超过则判定为不健康
     * @param deadLetterThreshold 死信条数告警阈值，超过则判定为不健康
     */
    public OutboxHealth(OutboxMessageRepository repository, long pendingThreshold, long deadLetterThreshold) {
        this.repository = repository;
        this.pendingThreshold = Math.max(pendingThreshold, 1);
        this.deadLetterThreshold = Math.max(deadLetterThreshold, 1);
    }

    /**
     * 采集一次积压状态。
     *
     * <p>仓储不支持统计（{@code countByStatus} 返回 -1）时判定为 {@link Status#UNKNOWN}，
     * 不伪装成健康：把"查不到"渲染成"积压为 0"比没有健康检查更危险。</p>
     */
    public Snapshot check() {
        long pending;
        long deadLetter;
        try {
            pending = repository.countByStatus(ReliableMessageStatus.PENDING);
            deadLetter = repository.countByStatus(ReliableMessageStatus.DEAD_LETTER);
        } catch (RuntimeException ex) {
            return new Snapshot(Status.DOWN, -1L, -1L,
                    detailsOf(-1L, -1L, "error", ex.getClass().getSimpleName()));
        }
        if (pending < 0 || deadLetter < 0) {
            return new Snapshot(Status.UNKNOWN, pending, deadLetter,
                    detailsOf(pending, deadLetter, "reason", "repository does not support countByStatus"));
        }
        Status status = pending > pendingThreshold || deadLetter > deadLetterThreshold ? Status.DOWN : Status.UP;
        return new Snapshot(status, pending, deadLetter, detailsOf(pending, deadLetter, null, null));
    }

    private Map<String, Object> detailsOf(long pending, long deadLetter, String extraKey, Object extraValue) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pending", pending);
        details.put("deadLetter", deadLetter);
        details.put("pendingThreshold", pendingThreshold);
        details.put("deadLetterThreshold", deadLetterThreshold);
        if (extraKey != null) {
            details.put(extraKey, extraValue);
        }
        return details;
    }

    /**
     * 健康状态。
     */
    public enum Status {

        /**
         * 积压在阈值内。
         */
        UP,

        /**
         * 积压超过阈值，或统计失败。
         */
        DOWN,

        /**
         * 仓储不支持统计。
         */
        UNKNOWN
    }

    /**
     * 一次采集结果。
     *
     * @param status 健康状态
     * @param pending 待派发条数，-1 表示不可用
     * @param deadLetter 死信条数，-1 表示不可用
     * @param details 明细，用于健康端点输出
     */
    public record Snapshot(Status status, long pending, long deadLetter, Map<String, Object> details) {
    }
}
