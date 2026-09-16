package com.michael.chaos.core.exception;

/**
 * 框架统一异常基类。
 *
 * <p>所有可预期的业务异常和框架异常都应携带 {@link ErrorCode}，
 * 便于 Web 层转换为统一响应结构，并保持错误码稳定。</p>
 */
public class ChaosException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码默认消息创建异常。
     *
     * @param errorCode 稳定错误码
     */
    public ChaosException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义展示消息创建异常。
     *
     * @param errorCode 稳定错误码
     * @param message 返回给调用方的错误消息
     */
    public ChaosException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义展示消息和底层异常创建异常。
     *
     * @param errorCode 稳定错误码
     * @param message 返回给调用方的错误消息
     * @param cause 底层异常原因
     */
    public ChaosException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 返回异常对应的稳定错误码。
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
