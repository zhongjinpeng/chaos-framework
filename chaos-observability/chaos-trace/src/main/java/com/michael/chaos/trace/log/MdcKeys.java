package com.michael.chaos.trace.log;

/**
 * 框架统一 MDC 字段名。
 *
 * <p>{@link #TRACE_ID} 与 {@link #SPAN_ID} 刻意与 Micrometer Tracing 使用同名字段，保证日志模板只需一套配置。
 * 为避免两套 ID 互相覆盖，Web 层在检测到 Micrometer Tracer 时会直接沿用其当前 span 的 ID
 * （见 chaos-web 的 {@code CurrentSpanProvider}），而不是自行生成。</p>
 */
public final class MdcKeys {

    /**
     * 链路追踪 ID。
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 当前调用跨度 ID。
     */
    public static final String SPAN_ID = "spanId";

    /**
     * 用户 ID。
     */
    public static final String USER_ID = "userId";

    /**
     * 租户 ID。
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 应用名称。
     */
    public static final String APP_NAME = "appName";

    /**
     * 请求 URI。
     */
    public static final String URI = "uri";

    /**
     * 客户端 IP。
     */
    public static final String IP = "ip";

    /**
     * 请求耗时，单位毫秒。
     */
    public static final String COST = "cost";

    private MdcKeys() {
    }
}
