package com.michael.chaos.service.retry;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/**
 * 基于 Spring Retry 的重试执行器。
 *
 * <p>无参 {@code execute} 使用注入的默认模板（框架默认不重试 {@code BizException} 等业务异常，
 * 业务规则校验失败重试多少次都不会成功，只会放大下游压力）；带 {@code retryOn} 的重载只重试指定异常。</p>
 */
public class SpringRetryExecutor implements RetryExecutor {

    private final RetryTemplate retryTemplate;

    private final int maxAttempts;

    private final long backoffMs;

    public SpringRetryExecutor(RetryTemplate retryTemplate) {
        this(retryTemplate, 3, 100);
    }

    public SpringRetryExecutor(RetryTemplate retryTemplate, int maxAttempts, long backoffMs) {
        this.retryTemplate = retryTemplate;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.backoffMs = Math.max(backoffMs, 0);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return retryTemplate.execute(context -> action.get());
    }

    @Override
    public void execute(Runnable action) {
        retryTemplate.execute(context -> {
            action.run();
            return null;
        });
    }

    @SafeVarargs
    @Override
    public final <T> T execute(Supplier<T> action, Class<? extends Throwable>... retryOn) {
        if (retryOn.length == 0) {
            return execute(action);
        }
        return scopedTemplate(retryOn).execute(context -> action.get());
    }

    @SafeVarargs
    @Override
    public final void execute(Runnable action, Class<? extends Throwable>... retryOn) {
        if (retryOn.length == 0) {
            execute(action);
            return;
        }
        scopedTemplate(retryOn).execute(context -> {
            action.run();
            return null;
        });
    }

    private RetryTemplate scopedTemplate(Class<? extends Throwable>[] retryOn) {
        RetryTemplateBuilder builder = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .retryOn(List.of(retryOn));
        return (backoffMs > 0 ? builder.fixedBackoff(backoffMs) : builder.noBackoff()).build();
    }
}
