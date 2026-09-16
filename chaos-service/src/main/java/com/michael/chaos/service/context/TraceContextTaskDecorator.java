package com.michael.chaos.service.context;

import com.michael.chaos.core.context.ContextPropagation;
import org.springframework.core.task.TaskDecorator;

/**
 * 异步任务上下文装饰器。
 *
 * <p>在任务提交线程捕获所有已注册上下文，在任务执行线程恢复，任务结束后再恢复执行线程原有上下文。</p>
 */
public class TraceContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return ContextPropagation.wrap(runnable);
    }
}
