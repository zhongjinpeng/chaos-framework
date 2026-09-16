package com.michael.chaos.core.diagnostic;

import java.util.Objects;

/**
 * 携带 {@link ChaosDiagnostic} 的启动/配置异常。
 *
 * <p>继承 {@link IllegalStateException}，已有 {@code catch (IllegalStateException)} 的调用方不受影响。
 * 异常消息即格式化后的"问题 / 原因 / 怎么修"文本，Spring Boot 启动失败时会原样打印在日志中。</p>
 *
 * <p>为什么不用 {@code FailureAnalyzer}：Spring Boot 3.5 只从 {@code META-INF/spring.factories} 加载 FailureAnalyzer，
 * 而本项目禁止 spring.factories；把可操作的指引直接放进异常消息，效果等价且不需要额外注册。</p>
 */
public class ChaosDiagnosticException extends IllegalStateException {

    private final transient ChaosDiagnostic diagnostic;

    /**
     * 使用诊断信息创建异常。
     */
    public ChaosDiagnosticException(ChaosDiagnostic diagnostic) {
        this(diagnostic, null);
    }

    /**
     * 使用诊断信息和底层原因创建异常。
     */
    public ChaosDiagnosticException(ChaosDiagnostic diagnostic, Throwable cause) {
        super(Objects.requireNonNull(diagnostic, "diagnostic must not be null").format(), cause);
        this.diagnostic = diagnostic;
    }

    /**
     * 返回结构化诊断信息。
     */
    public ChaosDiagnostic getDiagnostic() {
        return diagnostic;
    }
}
