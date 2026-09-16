package com.michael.chaos.core.context;

/**
 * 聚合所有已注册上下文访问器的快照。
 */
public final class ContextSnapshot {

    private final ContextAccessor<?>[] accessors;
    private final Object[] snapshots;

    ContextSnapshot(ContextAccessor<?>[] accessors, Object[] snapshots) {
        this.accessors = accessors;
        this.snapshots = snapshots;
    }

    boolean isEmpty() {
        return accessors.length == 0;
    }

    /**
     * 按注册顺序恢复所有上下文。
     *
     * <p>如果某个访问器 restore 抛出异常，必须把此前已经恢复成功的作用域逆序关闭后再抛出，
     * 否则执行线程会残留一半上下文，污染线程池中的后续任务。
     * 关闭作用域时同样逐个隔离异常，确保每个作用域都有机会还原。</p>
     */
    @SuppressWarnings("unchecked")
    ContextAccessor.Scope restore() {
        ContextAccessor.Scope[] scopes = new ContextAccessor.Scope[accessors.length];
        int restored = 0;
        try {
            for (; restored < accessors.length; restored++) {
                scopes[restored] = ((ContextAccessor<Object>) accessors[restored]).restore(snapshots[restored]);
            }
        } catch (RuntimeException | Error ex) {
            closeQuietly(scopes, restored, ex);
            throw ex;
        }
        return () -> {
            RuntimeException failure = null;
            for (int i = scopes.length - 1; i >= 0; i--) {
                try {
                    scopes[i].close();
                } catch (RuntimeException ex) {
                    if (failure == null) {
                        failure = ex;
                    } else {
                        failure.addSuppressed(ex);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        };
    }

    private static void closeQuietly(ContextAccessor.Scope[] scopes, int count, Throwable primary) {
        for (int i = count - 1; i >= 0; i--) {
            try {
                if (scopes[i] != null) {
                    scopes[i].close();
                }
            } catch (RuntimeException ex) {
                primary.addSuppressed(ex);
            }
        }
    }
}
