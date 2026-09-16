package com.michael.chaos.examples.order.domain;

/**
 * 订单聚合仓储（写模型）。
 *
 * <p>仓储只接收和返回聚合根，不暴露读模型；分页查询等读场景见应用层的 {@code OrderQueryRepository}。</p>
 */
public interface OrderRepository {

    /**
     * 保存新订单，并回填主键和创建时间。
     */
    Order save(Order order);
}
