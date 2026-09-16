package com.michael.chaos.test.context;

import com.michael.chaos.core.context.RequestContext;
import com.michael.chaos.core.context.RequestContextSnapshot;
import java.util.Optional;

/**
 * 测试用请求上下文夹具。
 *
 * <p>框架的 {@code RequestContext}、{@code TenantContext} 都是 ThreadLocal。测试里直接 {@code set} 后忘记清理，
 * 会让同一线程上后续执行的测试读到上一个测试的租户/用户，出现“单跑通过、全量失败”的串号问题。
 * 本类用 try-with-resources 保证作用域结束时一定恢复进入前的上下文：</p>
 *
 * <pre>{@code
 * try (ChaosTestContext.Scope ignored = ChaosTestContext.tenant("tenant-a").userId("1001").open()) {
 *     orderService.create(command);
 * }
 * }</pre>
 *
 * <p>classpath 中存在 chaos-tenant 时，会同步设置 {@code TenantContext}（状态为 ACTIVE）；否则只设置 RequestContext。
 * 租户/用户 ID 仍会经过 {@code RequestContextSnapshot} 的白名单校验，非法值会被置空，与生产行为一致。</p>
 */
public final class ChaosTestContext {

    private String traceId = "";

    private String spanId = "";

    private String tenantId = "";

    private String userId = "";

    private String appName = "test";

    private ChaosTestContext() {
    }

    /**
     * 以租户 ID 开始构建上下文。
     */
    public static ChaosTestContext tenant(String tenantId) {
        return new ChaosTestContext().tenantId(tenantId);
    }

    /**
     * 以用户 ID 开始构建上下文。
     */
    public static ChaosTestContext user(String userId) {
        return new ChaosTestContext().userId(userId);
    }

    /**
     * 创建空上下文构建器。
     */
    public static ChaosTestContext empty() {
        return new ChaosTestContext();
    }

    /**
     * 设置租户 ID。
     */
    public ChaosTestContext tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * 设置用户 ID。
     */
    public ChaosTestContext userId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * 设置 trace ID。
     */
    public ChaosTestContext traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * 设置 span ID。
     */
    public ChaosTestContext spanId(String spanId) {
        this.spanId = spanId;
        return this;
    }

    /**
     * 设置应用名。
     */
    public ChaosTestContext appName(String appName) {
        this.appName = appName;
        return this;
    }

    /**
     * 返回将要写入的快照，便于断言。
     */
    public RequestContextSnapshot snapshot() {
        return new RequestContextSnapshot(traceId, spanId, tenantId, userId, appName);
    }

    /**
     * 写入上下文并返回作用域；关闭作用域时恢复进入前的 RequestContext 与 TenantContext。
     */
    public Scope open() {
        Optional<RequestContextSnapshot> previousRequest = RequestContext.current();
        Object previousTenant = TenantContextBridge.capture();
        RequestContextSnapshot snapshot = snapshot();
        TenantContextBridge.activate(snapshot.tenantId());
        RequestContext.set(snapshot);
        return new Scope(previousRequest.orElse(null), previousTenant);
    }

    /**
     * 立即清理当前线程上的全部 chaos 测试上下文。
     */
    public static void clearAll() {
        RequestContext.clear();
        TenantContextBridge.clear();
    }

    /**
     * 上下文作用域，关闭时恢复进入前状态。
     */
    public static final class Scope implements AutoCloseable {

        private final RequestContextSnapshot previousRequest;

        private final Object previousTenant;

        private boolean closed;

        private Scope(RequestContextSnapshot previousRequest, Object previousTenant) {
            this.previousRequest = previousRequest;
            this.previousTenant = previousTenant;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            // 先恢复 TenantContext：TenantContext.set 会顺带改写 RequestContext 的租户 ID，最后再整体恢复 RequestContext。
            TenantContextBridge.restore(previousTenant);
            if (previousRequest == null) {
                RequestContext.clear();
            } else {
                RequestContext.set(previousRequest);
            }
        }
    }
}
