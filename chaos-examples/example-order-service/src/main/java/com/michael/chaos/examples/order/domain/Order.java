package com.michael.chaos.examples.order.domain;

import com.michael.chaos.domain.model.AggregateRoot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单聚合根。
 *
 * <p>业务规则（金额必须为正、新订单状态为 CREATED）集中在聚合内部，应用服务只负责编排。
 * 创建订单时注册 {@link OrderCreatedEvent}，由应用服务在同一事务内写入 outbox。</p>
 */
public class Order extends AggregateRoot<Long> {

    private Long id;

    private final String orderNo;

    private final String tenantId;

    private final String buyerId;

    private final BigDecimal amount;

    private OrderStatus status;

    private LocalDateTime createdAt;

    private Order(Long id, String orderNo, String tenantId, String buyerId, BigDecimal amount, OrderStatus status,
                  LocalDateTime createdAt) {
        this.id = id;
        this.orderNo = requireText(orderNo, "orderNo");
        this.tenantId = requireText(tenantId, "tenantId");
        this.buyerId = requireText(buyerId, "buyerId");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = createdAt;
    }

    /**
     * 创建新订单并注册订单创建事件。
     *
     * @throws IllegalArgumentException 金额不大于 0 或必填字段为空
     */
    public static Order create(String orderNo, String tenantId, String buyerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("order amount must be greater than 0");
        }
        Order order = new Order(null, orderNo, tenantId, buyerId, amount, OrderStatus.CREATED, null);
        order.registerEvent(OrderCreatedEvent.of(order, Instant.now()));
        return order;
    }

    /**
     * 从持久化数据还原订单，不产生领域事件。
     */
    public static Order restore(Long id, String orderNo, String tenantId, String buyerId, BigDecimal amount,
                                OrderStatus status, LocalDateTime createdAt) {
        return new Order(id, orderNo, tenantId, buyerId, amount, status, createdAt);
    }

    /**
     * 持久化后回填主键和创建时间。
     */
    public void markPersisted(Long id, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.createdAt = createdAt;
    }

    @Override
    public Long id() {
        return id;
    }

    public String orderNo() {
        return orderNo;
    }

    public String tenantId() {
        return tenantId;
    }

    public String buyerId() {
        return buyerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public OrderStatus status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
