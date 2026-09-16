package com.michael.chaos.examples.order;

import com.michael.chaos.examples.order.infra.messaging.LoggingMessagePublisher;
import com.michael.chaos.mq.MessagePublisher;
import com.michael.chaos.mybatis.datascope.DataScopeCondition;
import com.michael.chaos.mybatis.datascope.DataScopeProvider;
import com.michael.chaos.security.auth.SecurityUtils;
import com.michael.chaos.tenant.TenantDescriptor;
import com.michael.chaos.tenant.TenantIsolationMode;
import com.michael.chaos.tenant.TenantPlan;
import com.michael.chaos.tenant.TenantStatus;
import com.michael.chaos.tenant.TenantStatusProvider;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单服务示例配置。
 */
@Configuration(proxyBeanMethods = false)
public class ExampleOrderConfiguration {

    /**
     * 注册示例租户状态提供者。
     */
    @Bean
    public TenantStatusProvider tenantStatusProvider() {
        return tenantId -> switch (tenantId) {
            case "tenant-a", "tenant-b" -> new TenantDescriptor(
                    tenantId,
                    TenantStatus.ACTIVE,
                    TenantPlan.empty(),
                    TenantIsolationMode.SHARED_SCHEMA
            );
            case "tenant-frozen" -> new TenantDescriptor(
                    tenantId,
                    TenantStatus.FROZEN,
                    TenantPlan.empty(),
                    TenantIsolationMode.SHARED_SCHEMA
            );
            default -> TenantDescriptor.unknown(tenantId);
        };
    }

    /**
     * 注册示例消息发布器，让 outbox 派发链路在没有 Kafka/RocketMQ 时也能完整运行。
     */
    @Bean
    public MessagePublisher exampleMessagePublisher() {
        return new LoggingMessagePublisher();
    }

    /**
     * 注册示例数据权限提供者。
     *
     * <p>admin 角色可查看当前租户全部订单，普通用户只能查看自己作为买家的订单。</p>
     */
    @Bean
    public DataScopeProvider dataScopeProvider() {
        return scope -> {
            if (!"order".equals(scope)) {
                return Optional.empty();
            }
            return SecurityUtils.currentUser()
                    .map(user -> user.roles().contains("admin")
                            ? DataScopeCondition.eq("tenant_id", user.tenantId())
                            : DataScopeCondition.eq("buyer_id", user.userId()));
        };
    }
}
