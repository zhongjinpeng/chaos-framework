package com.michael.chaos.web.i18n;

/**
 * 框架内置消息 key。
 *
 * <p>业务系统可以在自己的 {@code messages.properties} 中定义同名 key 覆盖框架文案，
 * 无需替换 {@code GlobalExceptionHandler}。</p>
 */
public final class ChaosMessageKeys {

    /**
     * 错误码文案 key 前缀，完整 key 为 {@code chaos.error.<code>}。
     */
    public static final String ERROR_CODE_PREFIX = "chaos.error.";

    /**
     * 缺少必填请求参数。参数：参数名。
     */
    public static final String MISSING_PARAMETER = "chaos.web.missing-parameter";

    /**
     * 缺少必填请求头。参数：请求头名。
     */
    public static final String MISSING_HEADER = "chaos.web.missing-header";

    /**
     * 请求参数类型不匹配。参数：参数名、期望类型。
     */
    public static final String TYPE_MISMATCH = "chaos.web.type-mismatch";

    /**
     * 请求体无法解析。
     */
    public static final String MALFORMED_BODY = "chaos.web.malformed-body";

    /**
     * 不支持的请求方法。参数：请求方法。
     */
    public static final String METHOD_NOT_SUPPORTED = "chaos.web.method-not-supported";

    /**
     * 不支持的内容类型。参数：Content-Type。
     */
    public static final String MEDIA_TYPE_NOT_SUPPORTED = "chaos.web.media-type-not-supported";

    /**
     * 缺少或非法的幂等请求头。参数：请求头名。
     */
    public static final String MISSING_IDEMPOTENCY_KEY = "chaos.web.missing-idempotency-key";

    /**
     * 参数校验失败的单条字段提示。参数：字段名、校验消息。
     */
    public static final String VALIDATION_FIELD = "chaos.web.validation-field";

    private ChaosMessageKeys() {
    }
}
