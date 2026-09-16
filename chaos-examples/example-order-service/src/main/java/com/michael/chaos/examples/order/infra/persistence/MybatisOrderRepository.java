package com.michael.chaos.examples.order.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.michael.chaos.domain.dto.PageResult;
import com.michael.chaos.examples.order.application.query.OrderQueryRepository;
import com.michael.chaos.examples.order.application.query.OrderSummary;
import com.michael.chaos.examples.order.domain.Order;
import com.michael.chaos.examples.order.domain.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis Plus 的订单仓储实现，同时实现写模型仓储和读模型查询端口。
 */
@Repository
public class MybatisOrderRepository implements OrderRepository, OrderQueryRepository {

    private final OrderMapper orderMapper;

    /**
     * 创建订单仓储。
     */
    public MybatisOrderRepository(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 分页查询；单页上限由 chaos.mybatis.pagination.max-limit 兜底。
     */
    @Override
    public PageResult<OrderSummary> pageOrders(long current, long size) {
        Page<OrderEntity> page = orderMapper.selectPage(
                Page.of(current, size),
                new LambdaQueryWrapper<OrderEntity>().orderByDesc(OrderEntity::getId)
        );
        List<OrderSummary> records = page.getRecords().stream().map(this::toSummary).toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 插入订单并回填主键和审计字段。
     */
    @Override
    public Order save(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(order.orderNo());
        entity.setTenantId(order.tenantId());
        entity.setBuyerId(order.buyerId());
        entity.setAmount(order.amount());
        entity.setStatus(order.status());
        orderMapper.insert(entity);
        order.markPersisted(entity.getId(), entity.getCreatedAt());
        return order;
    }

    private OrderSummary toSummary(OrderEntity entity) {
        return new OrderSummary(
                entity.getId(),
                entity.getOrderNo(),
                entity.getTenantId(),
                entity.getBuyerId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
