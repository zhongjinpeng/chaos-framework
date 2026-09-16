package com.michael.chaos.examples.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 订单聚合根测试。
 */
class OrderTest {

    /**
     * 创建订单应进入 CREATED 状态并注册订单创建事件。
     */
    @Test
    void shouldRegisterCreatedEventWhenCreatingOrder() {
        Order order = Order.create("A-001", "tenant-a", "10001", new BigDecimal("12.50"));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.domainEvents()).singleElement()
                .isInstanceOfSatisfying(OrderCreatedEvent.class, event -> {
                    assertThat(event.aggregateId()).isEqualTo("A-001");
                    assertThat(event.tenantId()).isEqualTo("tenant-a");
                    assertThat(event.eventId()).isNotBlank();
                });
    }

    /**
     * 金额必须大于 0。
     */
    @Test
    void shouldRejectNonPositiveAmount() {
        assertThatThrownBy(() -> Order.create("A-002", "tenant-a", "10001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 从持久化数据还原时不产生事件。
     */
    @Test
    void shouldNotRegisterEventWhenRestoring() {
        Order order = Order.restore(1L, "A-003", "tenant-a", "10001", BigDecimal.TEN, OrderStatus.PAID, LocalDateTime.now());

        assertThat(order.domainEvents()).isEmpty();
        assertThat(order.id()).isEqualTo(1L);
    }
}
