package com.michael.chaos.core.context;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一上下文传播注册中心。
 *
 * <p>各模块通过 {@link #register(ContextAccessor)} 注册自己的上下文访问器，
 * 框架在跨线程传播时统一调用所有已注册访问器的 capture/restore。</p>
 */
public final class ContextPropagation {

    private static final System.Logger LOG = System.getLogger(ContextPropagation.class.getName());

    private static final List<ContextAccessor<?>> ACCESSORS = new CopyOnWriteArrayList<>();

    private ContextPropagation() {
    }

    /**
     * 注册上下文访问器。
     *
     * <p>加锁保证"判断是否已注册 + 追加"是原子操作，避免并发类加载时重复注册同类访问器。</p>
     */
    public static void register(ContextAccessor<?> accessor) {
        if (accessor == null) {
            return;
        }
        synchronized (ACCESSORS) {
            boolean alreadyRegistered = ACCESSORS.stream()
                    .anyMatch(existing -> existing.getClass() == accessor.getClass());
            if (!alreadyRegistered) {
                ACCESSORS.add(accessor);
            }
        }
    }

    /**
     * 捕获所有已注册上下文的快照。
     *
     * <p>先对 CopyOnWriteArrayList 取一次不可变数组，再按同一份数组捕获；
     * 原实现在循环中反复读取 {@code size()}，与并发注册竞争时两个数组长度可能不一致而越界。</p>
     */
    public static ContextSnapshot capture() {
        ContextAccessor<?>[] accessors = ACCESSORS.toArray(new ContextAccessor<?>[0]);
        Object[] snapshots = new Object[accessors.length];
        for (int i = 0; i < accessors.length; i++) {
            snapshots[i] = accessors[i].capture();
        }
        return new ContextSnapshot(accessors, snapshots);
    }

    /**
     * 恢复快照并返回聚合作用域。
     */
    public static ContextAccessor.Scope restore(ContextSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return () -> {};
        }
        return snapshot.restore();
    }

    /**
     * 使用当前上下文包装任务。
     */
    public static Runnable wrap(Runnable delegate) {
        ContextSnapshot snapshot = capture();
        return () -> {
            try (ContextAccessor.Scope ignored = restore(snapshot)) {
                delegate.run();
            }
        };
    }

    /**
     * 清理当前线程所有已注册上下文。
     *
     * <p>用于请求边界结束时兜底清理（例如 TraceFilter 的 finally），保证租户、数据权限等
     * 由业务代码 set 但忘记 clear 的 ThreadLocal 不会泄漏到下一个复用该线程的请求。
     * 单个访问器清理失败不影响其他访问器。</p>
     */
    public static void clearAll() {
        for (ContextAccessor<?> accessor : ACCESSORS.toArray(new ContextAccessor<?>[0])) {
            try {
                accessor.clear();
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to clear context accessor " + accessor.getClass().getName(), ex);
            }
        }
    }

    /**
     * 返回已注册的访问器数量（测试用）。
     */
    static int registeredCount() {
        return ACCESSORS.size();
    }
}
