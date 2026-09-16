package com.michael.chaos.service.retry;

import java.util.function.Supplier;

public interface RetryExecutor {

    <T> T execute(Supplier<T> action);

    void execute(Runnable action);

    @SuppressWarnings("unchecked")
    <T> T execute(Supplier<T> action, Class<? extends Throwable>... retryOn);

    @SuppressWarnings("unchecked")
    void execute(Runnable action, Class<? extends Throwable>... retryOn);
}
