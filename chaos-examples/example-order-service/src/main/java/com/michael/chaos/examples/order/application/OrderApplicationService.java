package com.michael.chaos.examples.order.application;

import com.michael.chaos.domain.model.DomainEvent;
import com.michael.chaos.domain.dto.PageResult;
import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.examples.order.application.query.OrderQueryRepository;
import com.michael.chaos.examples.order.application.query.OrderSummary;
import com.michael.chaos.examples.order.domain.Order;
import com.michael.chaos.examples.order.domain.OrderCreatedEvent;
import com.michael.chaos.examples.order.domain.OrderRepository;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.mq.reliable.OutboxPublisher;
import com.michael.chaos.security.annotation.DataScope;
import com.michael.chaos.security.annotation.Permission;
import com.michael.chaos.security.api.auth.LoginUser;
import com.michael.chaos.security.auth.SecurityUtils;
import com.michael.chaos.service.transaction.TransactionExecutor;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 订单应用服务。
 *
 * <p>演示 DDD 应用层职责：鉴权与数据权限注解、事务边界、调用聚合根、在同一事务内把领域事件写入 outbox。
 * 事务提交后由 outbox 派发器异步发送，业务数据与消息要么一起提交，要么一起回滚。</p>
 */
@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    private final OrderQueryRepository orderQueryRepository;

    private final OutboxPublisher outboxPublisher;

    private final TransactionExecutor transactionExecutor;

    /**
     * 创建订单应用服务。
     */
    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderQueryRepository orderQueryRepository,
            OutboxPublisher outboxPublisher,
            TransactionExecutor transactionExecutor) {
        this.orderRepository = orderRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.outboxPublisher = outboxPublisher;
        this.transactionExecutor = transactionExecutor;
    }

    /**
     * 分页查询订单，按数据权限 order 过滤。
     */
    @DataScope("order")
    @Permission("order:read")
    public PageResult<OrderSummary> pageOrders(long current, long size) {
        return orderQueryRepository.pageOrders(current, size);
    }

    /**
     * 创建当前登录用户所属租户的订单。
     *
     * <p>租户取自认证结果（JWT claim），而不是客户端请求头，避免伪造 X-Tenant-Id 跨租户写入。</p>
     */
    @Permission("order:create")
    public OrderSummary createOrder(CreateOrderCommand command) {
        LoginUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(CommonErrorCode.UNAUTHORIZED));
        if (user.tenantId() == null || user.tenantId().isBlank()) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "当前用户未绑定租户");
        }
        return transactionExecutor.execute(() -> {
            Order order = Order.create(command.orderNo(), user.tenantId(), command.buyerId(), command.amount());
            Order saved = orderRepository.save(order);
            saved.domainEvents().forEach(this::publishToOutbox);
            saved.clearDomainEvents();
            return OrderSummary.from(saved);
        });
    }

    private void publishToOutbox(DomainEvent event) {
        if (event instanceof OrderCreatedEvent created) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", created.aggregateId());
            payload.put("tenantId", created.tenantId());
            payload.put("buyerId", created.buyerId());
            payload.put("amount", created.amount());
            outboxPublisher.publish(new MessageEnvelope<>(
                    created.eventId(), OrderCreatedEvent.TOPIC, "created", payload, Map.of(), created.occurredAt()));
        }
    }

    /**
     * 创建订单命令。
     *
     * @param orderNo 订单编号
     * @param buyerId 买家 ID
     * @param amount 订单金额
     */
    public record CreateOrderCommand(String orderNo, String buyerId, BigDecimal amount) {
    }
}
