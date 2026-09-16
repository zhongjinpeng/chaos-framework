package com.michael.chaos.service.context;

import com.michael.chaos.core.context.ContextAccessor;
import com.michael.chaos.core.context.ContextPropagation;
import com.michael.chaos.core.context.ContextSnapshot;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 把 chaos 统一上下文桥接到 Micrometer Context Propagation。
 *
 * <p>{@link TraceContextTaskDecorator} 只覆盖 Spring {@code TaskExecutor}。{@code CompletableFuture}
 * 默认线程池、Reactor 算子等不经过 TaskDecorator 的异步边界会丢失 trace、租户和用户上下文。
 * 注册到 {@code ContextRegistry} 后，Reactor（{@code Hooks.enableAutomaticContextPropagation()}）、
 * {@code ContextExecutorService} 和 {@code ContextSnapshotFactory} 会自动传播 chaos 上下文。</p>
 *
 * <p>实现要点：Micrometer 以 set/restore 成对调用，这里用线程内栈保存 chaos 作用域，
 * restore 时按后进先出关闭，保证嵌套传播后执行线程恢复原状态。</p>
 */
public class ChaosContextThreadLocalAccessor implements ThreadLocalAccessor<ContextSnapshot> {

    /**
     * 在 ContextRegistry 中的唯一 key。
     */
    public static final String KEY = "chaos.context";

    private static final ThreadLocal<Deque<ContextAccessor.Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public ContextSnapshot getValue() {
        return ContextPropagation.capture();
    }

    @Override
    public void setValue(ContextSnapshot value) {
        SCOPES.get().push(ContextPropagation.restore(value));
    }

    /**
     * 目标线程没有该上下文时清空 chaos 上下文，并记录恢复点。
     */
    @Override
    public void setValue() {
        ContextSnapshot previous = ContextPropagation.capture();
        ContextPropagation.clearAll();
        SCOPES.get().push(() -> ContextPropagation.restore(previous));
    }

    @Override
    public void restore(ContextSnapshot previousValue) {
        closeLatestScope();
    }

    @Override
    public void restore() {
        closeLatestScope();
    }

    private static void closeLatestScope() {
        Deque<ContextAccessor.Scope> scopes = SCOPES.get();
        ContextAccessor.Scope scope = scopes.poll();
        if (scopes.isEmpty()) {
            SCOPES.remove();
        }
        if (scope != null) {
            scope.close();
        }
    }
}
