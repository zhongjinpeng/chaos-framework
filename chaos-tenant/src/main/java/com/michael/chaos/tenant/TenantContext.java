package com.michael.chaos.tenant;

import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.core.context.RequestContext;
import java.util.Optional;

/**
 * 当前线程租户上下文。
 *
 * <p>该上下文保存租户治理结果，供业务侧、MyBatis 和日志审计读取。租户 ID 仍以 `RequestContext`
 * 为主，避免出现两个租户 ID 来源。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantDescriptor> CONTEXT = new ThreadLocal<>();

    private static final ContextAccessor<TenantDescriptor> ACCESSOR = new ContextAccessor<>() {
        @Override
        public TenantDescriptor capture() {
            return CONTEXT.get();
        }

        @Override
        public Scope restore(TenantDescriptor snapshot) {
            TenantDescriptor previous = CONTEXT.get();
            if (snapshot != null) {
                CONTEXT.set(snapshot);
            } else {
                CONTEXT.remove();
            }
            return () -> {
                if (previous != null) {
                    CONTEXT.set(previous);
                } else {
                    CONTEXT.remove();
                }
            };
        }

        /**
         * 请求边界兜底清理。
         *
         * <p>业务代码 {@code TenantContext.set} 后如果忘记清理，Tomcat 线程复用时下一个请求会读到上一个租户，
         * MyBatis 租户插件又优先读取该值，会造成数据串租户。Web 层 TraceFilter 在请求结束时通过
         * {@code ContextPropagation.clearAll()} 调用这里。</p>
         */
        @Override
        public void clear() {
            CONTEXT.remove();
        }
    };

    static {
        ContextPropagation.register(ACCESSOR);
    }

    private TenantContext() {
    }

    /**
     * 设置当前线程租户描述，并同步租户 ID 到请求上下文。
     */
    public static void set(TenantDescriptor descriptor) {
        if (descriptor == null) {
            clear();
            return;
        }
        CONTEXT.set(descriptor);
        if (!descriptor.tenantId().isBlank()) {
            RequestContext.setTenantId(descriptor.tenantId());
        }
    }

    /**
     * 获取当前线程租户描述。
     */
    public static Optional<TenantDescriptor> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * 获取当前租户 ID。
     */
    public static String tenantId() {
        return current().map(TenantDescriptor::tenantId).orElseGet(RequestContext::tenantId);
    }

    /**
     * 获取当前租户状态。
     */
    public static TenantStatus status() {
        return current().map(TenantDescriptor::status).orElse(TenantStatus.UNKNOWN);
    }

    /**
     * 清理当前线程租户上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
