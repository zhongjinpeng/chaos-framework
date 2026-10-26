package com.chaos.service.json;

/**
 * JSON 编解码失败时抛出的统一技术异常。
 */
public class JsonOperationException extends RuntimeException {

    /**
     * 创建 JSON 操作异常。
     *
     * @param message 可定位失败操作的错误说明
     * @param cause Jackson 抛出的原始异常
     */
    public JsonOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
