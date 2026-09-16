package com.michael.chaos.examples.order.domain;

/**
 * 订单状态。
 */
public enum OrderStatus {

    /**
     * 已创建，等待履约。
     */
    CREATED,

    /**
     * 已完成。
     */
    PAID,

    /**
     * 已取消。
     */
    CANCELED
}
