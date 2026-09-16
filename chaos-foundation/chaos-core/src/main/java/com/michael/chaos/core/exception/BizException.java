package com.michael.chaos.core.exception;

/**
 * 业务可预期异常。
 *
 * <p>业务服务在参数不合法、状态冲突、权限不足等可预期场景下抛出该异常，
 * 由 Web 层统一转换为 {@code Result} 响应。</p>
 */
public class BizException extends ChaosException {

    /**
     * 使用错误码默认消息创建业务异常。
     *
     * @param errorCode 稳定错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 使用自定义展示消息创建业务异常。
     *
     * @param errorCode 稳定错误码
     * @param message 返回给调用方的错误消息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
