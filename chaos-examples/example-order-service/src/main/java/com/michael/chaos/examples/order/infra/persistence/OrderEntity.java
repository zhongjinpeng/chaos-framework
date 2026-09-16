package com.michael.chaos.examples.order.infra.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.michael.chaos.examples.order.domain.OrderStatus;
import com.michael.chaos.mybatis.entity.BaseEntity;
import java.math.BigDecimal;

/**
 * 订单持久化实体。
 */
@TableName("biz_order")
public class OrderEntity extends BaseEntity {

    /**
     * 订单主键。
     */
    @TableId
    private Long id;

    /**
     * 订单编号。
     */
    private String orderNo;

    /**
     * 租户 ID。
     */
    private String tenantId;

    /**
     * 买家 ID。
     */
    private String buyerId;

    /**
     * 订单金额。
     */
    private BigDecimal amount;

    /**
     * 订单状态。
     */
    @TableField("status")
    private OrderStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
