package com.michael.chaos.mq.reliable.actuate;

import com.michael.chaos.mq.reliable.OutboxHealth;

import java.util.Objects;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * outbox 积压健康指示器。
 *
 * <p>把 {@link OutboxHealth} 的判定结果适配为 Spring Boot Actuator 健康项，
 * 注册后出现在 {@code /actuator/health} 的 {@code chaosMqOutbox} 下。</p>
 *
 * <p>单独放在 {@code reliable.actuate} 适配器包：chaos-mq 是契约模块，主包不允许出现 Spring 类型。
 * 只有引入 Actuator 的应用才会加载本类，{@code OutboxHealth} 的判定逻辑本身保持框架无关。</p>
 */
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxHealth outboxHealth;

    /**
     * 创建健康指示器。
     */
    public OutboxHealthIndicator(OutboxHealth outboxHealth) {
        this.outboxHealth = Objects.requireNonNull(outboxHealth, "outboxHealth must not be null");
    }

    @Override
    public Health health() {
        OutboxHealth.Snapshot snapshot = outboxHealth.check();
        Health.Builder builder = switch (snapshot.status()) {
            case UP -> Health.up();
            case DOWN -> Health.down();
            case UNKNOWN -> Health.unknown();
        };
        snapshot.details().forEach(builder::withDetail);
        return builder.build();
    }
}
