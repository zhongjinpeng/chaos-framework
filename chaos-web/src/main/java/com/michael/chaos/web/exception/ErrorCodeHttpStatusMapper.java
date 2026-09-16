package com.michael.chaos.web.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码到 HTTP 状态码的映射策略。
 */
@FunctionalInterface
public interface ErrorCodeHttpStatusMapper {

    /**
     * 将业务错误码映射为 HTTP 状态码。
     *
     * @param code 业务错误码
     * @return HTTP 状态码，返回 null 时使用框架默认映射
     */
    HttpStatus resolve(String code);
}
