package com.michael.chaos.core.context;

/**
 * 上下文传播访问器。
 *
 * <p>各模块实现此接口并注册到 {@link ContextPropagation}，
 * 即可参与统一的跨线程上下文传播。</p>
 *
 * @param <S> 快照类型
 */
public interface ContextAccessor<S> {

    /**
     * 捕获当前线程的上下文快照。
     */
    S capture();

    /**
     * 在当前线程恢复快照，返回可关闭作用域。
     *
     * <p>{@code snapshot} 为 {@code null} 时表示恢复为"无上下文"状态。</p>
     */
    Scope restore(S snapshot);

    /**
     * 清理当前线程上下文。
     *
     * <p>请求边界（例如 Servlet 最外层过滤器）结束时调用，防止线程池复用导致上下文串扰。
     * 默认实现等价于恢复空快照且不再还原；持有额外资源的访问器可以覆盖该方法。</p>
     */
    default void clear() {
        restore(null);
    }

    /**
     * 上下文作用域，关闭时恢复进入前的状态。
     */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
