package com.michael.chaos.core.context;

import java.util.regex.Pattern;

/**
 * 上下文标识符（租户 ID、用户 ID、trace ID）的格式校验工具。
 *
 * <p>租户 ID 和用户 ID 会被写入 MDC、响应头、出站请求头，并被 MyBatis 租户插件拼接进 SQL。
 * 如果允许任意字符进入上下文，攻击者可以通过伪造请求头注入单引号、换行符等控制字符，
 * 造成 SQL 注入、日志注入或响应头拆分。因此所有进入 {@link RequestContextSnapshot} 的标识符
 * 都必须通过白名单校验，校验失败的值统一视为"缺失"（空字符串），让下游按缺失策略 fail-closed。</p>
 *
 * <p>白名单刻意不做成可配置项：放宽规则（例如允许引号）会直接重新打开 SQL 注入入口，
 * 业务如确有特殊格式，应在认证层把外部标识映射为内部 ID 后再写入上下文。</p>
 */
public final class ContextIdentifiers {

    /**
     * 标识符最大长度，与审计表和常见租户表字段长度保持一致。
     */
    public static final int MAX_IDENTIFIER_LENGTH = 128;

    /**
     * 兼容旧系统 trace/span ID 的最大长度。
     */
    public static final int MAX_TRACE_ID_LENGTH = 64;

    /**
     * 租户 ID、用户 ID 白名单：字母、数字以及 {@code _ - . : @}，不允许引号、空白和控制字符。
     */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[A-Za-z0-9_.:@-]{1," + MAX_IDENTIFIER_LENGTH + "}$");

    /**
     * 旧系统 traceId/spanId 白名单：字母、数字、下划线和中划线。
     */
    private static final Pattern TRACE_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1," + MAX_TRACE_ID_LENGTH + "}$");

    private static final System.Logger LOG = System.getLogger(ContextIdentifiers.class.getName());

    private ContextIdentifiers() {
    }

    /**
     * 判断租户 ID 或用户 ID 是否合法。
     *
     * @param value 待校验值
     * @return 合法返回 {@code true}；{@code null} 或空白返回 {@code false}
     */
    public static boolean isValidIdentifier(String value) {
        return value != null && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    /**
     * 判断兼容旧系统的 traceId/spanId 是否合法。
     *
     * @param value 待校验值
     * @return 合法返回 {@code true}
     */
    public static boolean isValidTraceId(String value) {
        return value != null && TRACE_ID_PATTERN.matcher(value).matches();
    }

    /**
     * 规范化租户 ID 或用户 ID：去除首尾空白，非法值返回空字符串。
     *
     * <p>非法值只记录长度而不记录原文，避免把攻击载荷写入日志。</p>
     *
     * @param field 字段名，仅用于日志
     * @param value 原始值
     * @return 合法值或空字符串
     */
    public static String sanitizeIdentifier(String field, String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (isValidIdentifier(trimmed)) {
            return trimmed;
        }
        LOG.log(System.Logger.Level.WARNING,
                "Rejected illegal context identifier: field={0}, length={1}", field, trimmed.length());
        return "";
    }

    /**
     * 规范化 traceId/spanId：非法值返回空字符串，由调用方决定是否重新生成。
     *
     * @param value 原始值
     * @return 合法值或空字符串
     */
    public static String sanitizeTraceId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return isValidTraceId(trimmed) ? trimmed : "";
    }
}
