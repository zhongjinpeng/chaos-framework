package com.michael.chaos.mq.consumer;

import com.michael.chaos.core.constant.ChaosHeaders;
import com.michael.chaos.mq.MessageEnvelope;
import com.michael.chaos.trace.TraceContext;
import com.michael.chaos.trace.TraceContextSnapshot;
import java.util.Map;
import java.util.Objects;

/**
 * 在消费期间还原消息头中 trace、租户、用户上下文的消费者包装器。
 *
 * <p>发布端通过 {@link MessageEnvelope#withTraceHeaders()} 把上下文写入消息头，但消费线程是 MQ listener 线程，
 * 没有请求上下文。不还原时日志无法串联 traceId，MyBatis 租户插件在 {@code missing-tenant-behavior=DENY}
 * 下会直接拒绝 SQL。</p>
 *
 * <p>信任边界：消息头来自内部服务写入的 MQ，默认视为可信；不要把外部系统可以直接投递的 topic 接到该包装器上。
 * 消费结束后恢复进入前的上下文，避免 listener 线程复用时串用上一条消息的租户。</p>
 *
 * @param <T> 消息体类型
 */
public class ContextRestoringMessageConsumer<T> implements MessageConsumer<T> {

    private final MessageConsumer<T> delegate;

    private final String appName;

    /**
     * 创建上下文还原消费者。
     *
     * @param delegate 业务消费者
     * @param appName 当前应用名，写入 MDC
     */
    public ContextRestoringMessageConsumer(MessageConsumer<T> delegate, String appName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.appName = appName == null ? "" : appName;
    }

    /**
     * 还原上下文后消费消息，结束后恢复原上下文。
     */
    @Override
    public void consume(MessageEnvelope<T> message) {
        TraceContextSnapshot previous = TraceContext.capture();
        Map<String, String> headers = message.headers();
        try {
            TraceContext.startServer(
                    headers.get(ChaosHeaders.TRACE_ID),
                    headers.get(ChaosHeaders.TRACEPARENT),
                    headers.get(ChaosHeaders.TRACESTATE),
                    headers.get(ChaosHeaders.BAGGAGE),
                    headers.get(ChaosHeaders.TENANT_ID),
                    headers.get(ChaosHeaders.USER_ID),
                    appName
            );
            delegate.consume(message);
        } finally {
            // restore 会把线程恢复为进入前的状态；返回的 scope 指向消息上下文，不需要关闭。
            TraceContext.restore(previous);
        }
    }
}
