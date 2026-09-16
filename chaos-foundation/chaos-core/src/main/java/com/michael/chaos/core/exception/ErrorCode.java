package com.michael.chaos.core.exception;

/**
 * 错误码契约。
 *
 * <p>错误码是框架、业务服务、网关和前端之间的稳定边界，不应直接依赖异常类名或消息文本做逻辑判断。</p>
 */
public interface ErrorCode {

    /**
     * 返回对外稳定错误码。
     */
    String code();

    /**
     * 返回默认错误消息。
     *
     * <p>这是没有配置国际化消息、或当前语言没有对应条目时的兜底文案，因此应当是一段与语言环境无关、
     * 面向开发者可读的短描述，而不是给终端用户看的最终文案。</p>
     */
    String message();

    /**
     * 返回国际化消息 key。
     *
     * <p>Web 层会先用该 key 查 {@code MessageSource}，查不到再退回 {@link #message()}。
     * 默认按 {@code chaos.error.<code>} 约定生成，业务错误码枚举可以覆盖该方法使用自己的 key 命名空间。</p>
     *
     * @return 消息 key，返回 {@code null} 或空串表示该错误码不参与国际化
     */
    default String messageKey() {
        return "chaos.error." + code();
    }
}
