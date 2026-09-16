package com.michael.chaos.core.exception;

import java.util.Locale;

/**
 * 框架内置通用错误码。
 *
 * <p>业务系统可以定义自己的 {@link ErrorCode} 枚举，但不应修改这些基础错误码语义，
 * 以保证跨服务响应和监控统计口径一致。</p>
 */
public enum CommonErrorCode implements ErrorCode {

    SUCCESS("0", "success"),
    BAD_REQUEST("400", "bad request"),
    UNAUTHORIZED("401", "unauthorized"),
    FORBIDDEN("403", "forbidden"),
    NOT_FOUND("404", "not found"),
    METHOD_NOT_ALLOWED("405", "method not allowed"),
    UNSUPPORTED_MEDIA_TYPE("415", "unsupported media type"),
    TOO_MANY_REQUESTS("429", "too many requests"),
    IDEMPOTENT_REJECTED("409", "duplicate request"),
    INTERNAL_ERROR("500", "internal server error"),
    SERVICE_UNAVAILABLE("503", "service unavailable");

    private final String code;

    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 返回对外稳定错误码。
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 返回默认错误消息。
     */
    @Override
    public String message() {
        return message;
    }

    /**
     * 返回内置错误码专用的国际化消息 key，形如 {@code chaos.error.common.bad-request}。
     *
     * <p>不沿用 {@link ErrorCode#messageKey()} 的 {@code chaos.error.<code>} 约定：那个命名空间留给业务错误码，
     * 而业务枚举里出现 {@code 404}、{@code 500} 这类数字码相当常见，共用命名空间会让业务文案被框架文案顶掉。</p>
     */
    @Override
    public String messageKey() {
        return "chaos.error.common." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
