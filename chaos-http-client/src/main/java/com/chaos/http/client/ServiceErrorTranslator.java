package com.chaos.http.client;

/**
 * 将下游服务的业务错误码翻译为调用方异常。
 */
@FunctionalInterface
public interface ServiceErrorTranslator {

    /**
     * 翻译下游错误码。
     *
     * @param code 下游返回的业务错误码
     * @param message 下游返回的错误文案
     * @return 调用方应抛出的异常
     */
    RuntimeException translate(String code, String message);
}
