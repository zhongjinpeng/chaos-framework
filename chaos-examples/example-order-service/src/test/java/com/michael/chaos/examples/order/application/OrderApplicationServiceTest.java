package com.michael.chaos.examples.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michael.chaos.domain.dto.PageResult;
import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.examples.order.application.query.OrderSummary;
import com.michael.chaos.examples.order.domain.Order;
import com.michael.chaos.examples.order.domain.OrderCreatedEvent;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.service.transaction.TransactionExecutor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 订单应用服务测试。
 */
class OrderApplicationServiceTest {

    private final List<MessageEnvelope<?>> outbox = new ArrayList<>();

    private final List<Order> saved = new ArrayList<>();

    private final OrderApplicationService service = new OrderApplicationService(
            order -> {
                order.markPersisted(100L, LocalDateTime.now());
                saved.add(order);
                return order;
            },
            (current, size) -> new PageResult<>(List.of(), 0, current, size),
            outbox::add,
            new DirectTransactionExecutor());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 创建订单时租户取自登录用户，并在事务内写入 outbox。
     */
    @Test
    void shouldUseAuthenticatedTenantAndWriteOutbox() {
        authenticate("tenant-a");

        OrderSummary summary = service.createOrder(
                new OrderApplicationService.CreateOrderCommand("A-100", "10001", new BigDecimal("9.90")));

        assertThat(summary.id()).isEqualTo(100L);
        assertThat(summary.tenantId()).isEqualTo("tenant-a");
        assertThat(outbox).singleElement().satisfies(message -> {
            assertThat(message.topic()).isEqualTo(OrderCreatedEvent.TOPIC);
            assertThat(message.messageId()).isNotBlank();
        });
        assertThat(saved.getFirst().domainEvents()).isEmpty();
    }

    /**
     * 未登录时拒绝创建订单。
     */
    @Test
    void shouldRejectUnauthenticatedCreation() {
        assertThatThrownBy(() -> service.createOrder(
                new OrderApplicationService.CreateOrderCommand("A-101", "10001", BigDecimal.ONE)))
                .isInstanceOf(BizException.class);
        assertThat(outbox).isEmpty();
    }

    private static void authenticate(String tenantId) {
        LoginUser user = new LoginUser("10001", "alice", tenantId, Set.of("user"), Set.of("order:create"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static final class DirectTransactionExecutor implements TransactionExecutor {

        @Override
        public <T> T execute(Supplier<T> action) {
            return action.get();
        }

        @Override
        public void execute(Runnable action) {
            action.run();
        }
    }
}
