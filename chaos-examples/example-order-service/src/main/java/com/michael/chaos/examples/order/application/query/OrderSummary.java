package com.michael.chaos.examples.order.application.query;

import com.michael.chaos.examples.order.domain.Order;
import com.michael.chaos.examples.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单摘要读模型。
 *
 * @param id 订单 ID
 * @param orderNo 订单编号
 * @param tenantId 租户 ID
 * @param buyerId 买家 ID
 * @param amount 订单金额
 * @param status 订单状态
 * @param createdAt 创建时间
 */
public record OrderSummary(
        Long id,
        String orderNo,
        String tenantId,
        String buyerId,
        BigDecimal amount,
        OrderStatus status,
        LocalDateTime createdAt
) {

    /**
     * 从聚合根转换。
     */
    public static OrderSummary from(Order order) {
        return new OrderSummary(order.id(), order.orderNo(), order.tenantId(), order.buyerId(), order.amount(),
                order.status(), order.createdAt());
    }
}
