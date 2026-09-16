package com.michael.chaos.mq.reliable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 可靠消息 outbox 仓储端口。
 *
 * <p>该端口只定义语义，不绑定 JDBC、MyBatis、Redis 或具体 MQ。业务系统或后续 adapter 可以按数据库实现。</p>
 */
public interface OutboxMessageRepository {

    /**
     * 保存可靠消息。
     */
    void save(ReliableMessage message);

    /**
     * 根据消息 ID 查询可靠消息。
     *
     * <p>只有“记录不存在”才返回空；连接失败等基础设施异常必须抛出，不能伪装成不存在。</p>
     */
    Optional<ReliableMessage> findByMessageId(String messageId);

    /**
     * 查找到期可发送的消息。
     *
     * @param now 当前时间
     * @param limit 最大返回条数
     * @return 待发送消息列表
     */
    List<ReliableMessage> findDueMessages(Instant now, int limit);

    /**
     * 抢占一批到期可发送的消息。
     *
     * <p>实现必须保证原子性：同一条消息同一时刻只被一个派发器获取。
     * 推荐通过数据库条件更新（UPDATE ... WHERE status = PENDING）、行锁或队列可见性超时实现。</p>
     *
     * @param now 当前时间
     * @param limit 最大返回条数
     * @return 已抢占消息列表
     */
    List<ReliableMessage> claimDueMessages(Instant now, int limit);

    /**
     * 抢占一批到期或发送超时的消息。
     *
     * <p>默认实现仅领取到期消息。支持 claim 超时恢复的仓储应覆盖该方法，把长时间停留
     * 在 SENDING 的消息重新纳入派发，并把回收计入重试次数。</p>
     *
     * @param now 当前时间
     * @param limit 最大返回条数
     * @param claimTimeoutAt claim 超时边界时间
     * @return 已抢占消息列表
     */
    default List<ReliableMessage> claimDueMessages(Instant now, int limit, Instant claimTimeoutAt) {
        return claimDueMessages(now, limit);
    }

    /**
     * 统计处于指定状态的消息条数。
     *
     * <p>派发器卡住（claim 持有者崩溃、重试全部耗尽）此前只在日志里留痕，监控上完全不可见。
     * 有了这个计数，积压深度和死信堆积才能进入指标和健康检查。</p>
     *
     * <p>默认实现返回 {@code -1} 表示"不支持统计"，调用方应据此跳过上报而不是当作 0——
     * 把"查不到"渲染成"积压为 0"比没有指标更危险。</p>
     *
     * @param status 消息状态
     * @return 条数；不支持统计时返回 {@code -1}
     */
    default long countByStatus(ReliableMessageStatus status) {
        return -1L;
    }

    /**
     * 更新消息状态。
     */
    void update(ReliableMessage message);

    /**
     * 结束一次 claim：把当前实例抢占的 SENDING 消息更新为终态或重试态。
     *
     * <p>实现必须校验“状态仍为 SENDING 且 claim 属于当前实例”。否则实例 A 发送卡住、实例 B 回收并发送成功后，
     * A 迟到的失败结果会把 SENT 覆盖为 RETRYING，造成重复投递。默认实现退化为 {@link #update}，
     * 仅用于不支持 claim owner 的简单仓储。</p>
     *
     * @param message 目标状态消息
     * @return 当前实例仍持有 claim 且更新成功时返回 {@code true}
     */
    default boolean completeClaim(ReliableMessage message) {
        update(message);
        return true;
    }

    /**
     * 删除早于指定时间的已发送消息。
     *
     * <p>SENT 记录没有业务价值，不清理会让 outbox 表无限增长并拖慢抢占查询。默认实现不删除。</p>
     *
     * @param sentBefore 删除 {@code updated_at} 早于该时间的 SENT 记录
     * @param limit 单批最大删除条数
     * @return 实际删除条数
     */
    default int deleteSentMessagesBefore(Instant sentBefore, int limit) {
        return 0;
    }
}
