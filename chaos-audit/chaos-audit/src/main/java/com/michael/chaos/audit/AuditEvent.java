package com.michael.chaos.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 统一审计事件。
 *
 * @param action 动作编码
 * @param outcome 执行结果
 * @param principalId 主体 ID，通常是用户 ID
 * @param tenantId 租户 ID
 * @param clientId OAuth2 客户端 ID 或调用方应用
 * @param traceId 链路追踪 ID
 * @param ip 客户端 IP
 * @param uri 请求 URI
 * @param reason 失败或拒绝原因
 * @param occurredAt 事件发生时间
 * @param attributes 扩展属性
 */
public record AuditEvent(
        String action,
        AuditOutcome outcome,
        String principalId,
        String tenantId,
        String clientId,
        String traceId,
        String ip,
        String uri,
        String reason,
        Instant occurredAt,
        Map<String, String> attributes
) {

    /**
     * 规范化审计字段，保证发布器无需处理空指针。
     */
    public AuditEvent {
        action = requireText(action, "action");
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        principalId = normalize(principalId);
        tenantId = normalize(tenantId);
        clientId = normalize(clientId);
        traceId = normalize(traceId);
        ip = normalize(ip);
        uri = normalize(uri);
        reason = normalize(reason);
        occurredAt = Objects.requireNonNullElseGet(occurredAt, Instant::now);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 创建审计事件构造器。
     */
    public static Builder builder(String action, AuditOutcome outcome) {
        return new Builder(action, outcome);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    /**
     * 审计事件构造器。
     */
    public static final class Builder {

        private final String action;

        private final AuditOutcome outcome;

        private String principalId;

        private String tenantId;

        private String clientId;

        private String traceId;

        private String ip;

        private String uri;

        private String reason;

        private Instant occurredAt;

        private Map<String, String> attributes = Map.of();

        private Builder(String action, AuditOutcome outcome) {
            this.action = action;
            this.outcome = outcome;
        }

        /**
         * 设置主体 ID。
         */
        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        /**
         * 设置租户 ID。
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置客户端 ID。
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * 设置 traceId。
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 设置客户端 IP。
         */
        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        /**
         * 设置请求 URI。
         */
        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * 设置原因。
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * 设置事件发生时间。
         */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * 设置扩展属性。
         */
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * 构建审计事件。
         */
        public AuditEvent build() {
            return new AuditEvent(
                    action,
                    outcome,
                    principalId,
                    tenantId,
                    clientId,
                    traceId,
                    ip,
                    uri,
                    reason,
                    occurredAt,
                    attributes
            );
        }
    }
}
