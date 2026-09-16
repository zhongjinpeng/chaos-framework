package com.michael.chaos.examples.order.application.query;

import com.michael.chaos.domain.dto.PageResult;

/**
 * 订单查询端口（读模型）。
 *
 * <p>读写分离：查询直接返回读模型，不经过聚合根，避免为展示需求污染领域模型。</p>
 */
public interface OrderQueryRepository {

    /**
     * 分页查询当前租户订单，租户条件由 MyBatis 多租户插件追加。
     */
    PageResult<OrderSummary> pageOrders(long current, long size);
}
