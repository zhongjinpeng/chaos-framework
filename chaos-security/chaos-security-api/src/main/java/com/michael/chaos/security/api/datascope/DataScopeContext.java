package com.michael.chaos.security.api.datascope;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 当前线程数据权限上下文。
 */
public final class DataScopeContext {

    private static final ThreadLocal<Deque<DataScopeRequest>> CURRENT = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /**
     * 替换当前（栈顶）数据权限策略编码。
     */
    public static void set(String scope) {
        set(DataScopeRequest.of(scope));
    }

    /**
     * 替换当前（栈顶）数据权限请求，外层嵌套上下文保持不变。
     *
     * <p>旧实现会清空整个栈，嵌套 {@code @DataScope} 调用 set 后，外层切面在 finally 中 pop 时
     * 外层作用域已经丢失，后续查询会在没有数据权限的情况下执行。嵌套场景请优先使用 {@link #push}/{@link #pop}。</p>
     */
    public static void set(DataScopeRequest request) {
        Deque<DataScopeRequest> stack = CURRENT.get();
        if (stack != null && !stack.isEmpty()) {
            stack.pop();
            if (stack.isEmpty()) {
                CURRENT.remove();
            }
        }
        push(request);
    }

    /**
     * 压入数据权限策略编码，用于嵌套数据权限场景。
     *
     * @return 是否压入成功
     */
    public static boolean push(String scope) {
        return push(DataScopeRequest.of(scope));
    }

    /**
     * 压入数据权限请求，用于嵌套数据权限场景。
     *
     * @return 是否压入成功
     */
    public static boolean push(DataScopeRequest request) {
        if (request == null || request.scope().isBlank()) {
            return false;
        }
        Deque<DataScopeRequest> stack = CURRENT.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            CURRENT.set(stack);
        }
        stack.push(request);
        return true;
    }

    /**
     * 弹出当前数据权限请求，恢复外层上下文。
     */
    public static void pop() {
        Deque<DataScopeRequest> stack = CURRENT.get();
        if (stack == null) {
            return;
        }
        stack.poll();
        if (stack.isEmpty()) {
            CURRENT.remove();
        }
    }

    /**
     * 获取当前数据权限策略编码。
     */
    public static String current() {
        return currentRequest().map(DataScopeRequest::scope).orElse("");
    }

    /**
     * 获取当前数据权限请求。
     */
    public static Optional<DataScopeRequest> currentRequest() {
        Deque<DataScopeRequest> stack = CURRENT.get();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stack.peek());
    }

    /**
     * 清理当前线程数据权限上下文。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
