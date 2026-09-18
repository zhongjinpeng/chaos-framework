package com.michael.chaos.examples.order.interfaces.rest;

import com.michael.chaos.domain.dto.PageResult;
import com.michael.chaos.examples.order.application.OrderApplicationService;
import com.michael.chaos.examples.order.application.query.OrderSummary;
import com.michael.chaos.security.annotation.AccessAttribute;
import com.michael.chaos.security.annotation.RequireAccess;
import com.michael.chaos.web.idempotent.Idempotent;
import com.michael.chaos.web.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口适配器。
 */
@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    /**
     * 创建订单接口适配器。
     */
    public OrderController(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * 健康探测接口，便于不带 token 验证服务是否启动。
     */
    @GetMapping("/ping")
    @RateLimit(permitsPerSecond = 50)
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "time", Instant.now());
    }

    /**
     * 分页查询当前租户订单。
     *
     * <p>接口层限制单页最多 100 条，MyBatis 分页插件的 max-limit 作为兜底。</p>
     */
    @GetMapping
    @RateLimit(permitsPerSecond = 20)
    @RequireAccess(action = "order:read", resourceType = "order")
    public PageResult<OrderSummary> pageOrders(
            @RequestParam(defaultValue = "1") @Positive long current,
            @RequestParam(defaultValue = "10") @Positive @Max(100) long size) {
        return orderApplicationService.pageOrders(current, size);
    }

    /**
     * 创建当前租户订单。
     *
     * <p>演示 ABAC：动作要求 {@code order:create} 权限，同时把订单金额作为资源属性交给授权策略，
     * 大额订单的拒绝规则写在 {@code chaos.security.access.policies} 配置里，不需要改代码。</p>
     */
    @PostMapping
    @Idempotent
    @RequireAccess(
            action = "order:create",
            resourceType = "order",
            resourceId = "#request.orderNo",
            attributes = {
                    @AccessAttribute(name = "amount", value = "#request.amount"),
                    @AccessAttribute(name = "buyerId", value = "#request.buyerId")
            })
    public OrderSummary createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderApplicationService.createOrder(new OrderApplicationService.CreateOrderCommand(
                request.orderNo(),
                request.buyerId(),
                request.amount()
        ));
    }

    /**
     * 创建订单请求。
     *
     * @param orderNo 订单编号
     * @param buyerId 买家 ID
     * @param amount 订单金额
     */
    public record CreateOrderRequest(
            @NotBlank(message = "订单编号不能为空") String orderNo,
            @NotBlank(message = "买家 ID 不能为空") String buyerId,
            @NotNull(message = "订单金额不能为空") @DecimalMin(value = "0.01", message = "订单金额必须大于 0") BigDecimal amount
    ) {
    }
}
