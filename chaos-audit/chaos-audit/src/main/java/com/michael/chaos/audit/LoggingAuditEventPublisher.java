package com.michael.chaos.audit;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于日志的默认审计事件发布器。
 *
 * <p>默认实现不依赖数据库和 MQ，适合作为平台兜底能力。生产环境可以按需替换为持久化发布器。</p>
 *
 * <p>输出前必须经过 {@link AuditAttributeSanitizer} 脱敏：原实现直接打印 attributes，
 * 登录失败等事件中的密码、验证码会原样进入日志平台。</p>
 */
public class LoggingAuditEventPublisher implements AuditEventPublisher {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOG");

    private final AuditAttributeSanitizer attributeSanitizer;

    /**
     * 使用默认脱敏策略创建日志审计发布器。
     */
    public LoggingAuditEventPublisher() {
        this(new DefaultAuditAttributeSanitizer());
    }

    /**
     * 使用指定脱敏策略创建日志审计发布器。
     *
     * @param attributeSanitizer 扩展属性脱敏器
     */
    public LoggingAuditEventPublisher(AuditAttributeSanitizer attributeSanitizer) {
        this.attributeSanitizer = Objects.requireNonNull(attributeSanitizer, "attributeSanitizer must not be null");
    }

    /**
     * 输出结构化审计日志。
     */
    @Override
    public void publish(AuditEvent event) {
        auditLog.info(
                "action={} outcome={} principalId={} tenantId={} clientId={} traceId={} ip={} uri={} reason={} attributes={}",
                event.action(),
                event.outcome(),
                event.principalId(),
                event.tenantId(),
                event.clientId(),
                event.traceId(),
                event.ip(),
                event.uri(),
                oneLine(event.reason()),
                attributeSanitizer.sanitize(event.attributes())
        );
    }

    /**
     * reason 可能包含异常消息，去掉换行防止伪造审计日志行。
     */
    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
