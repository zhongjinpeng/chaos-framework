package com.michael.chaos.security.api.access;

/**
 * 框架内置的 ABAC 环境属性名。
 *
 * <p>资源服务器（chaos-security 的环境属性贡献者）和网关（chaos-gateway 的鉴权过滤器）各写一套
 * 属性名字符串，任何一边改动都会让使用方配置里的 {@code environment.xxx} 条件悄悄失效——
 * 策略不报错，只是永远不命中。属性名因此集中在这里，两边共用。</p>
 *
 * <p>业务自定义的环境属性不需要登记，直接用 {@code AuthorizationContextContributor} 写入即可。</p>
 */
public final class AccessEnvironment {

    /**
     * 客户端 IP，按可信代理规则解析后的结果。
     */
    public static final String CLIENT_IP = "clientIp";

    /**
     * 请求路径。
     */
    public static final String URI = "uri";

    /**
     * HTTP 方法。
     */
    public static final String HTTP_METHOD = "http.method";

    /**
     * HTTP 请求路径。
     */
    public static final String HTTP_PATH = "http.path";

    /**
     * 链路 ID。
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 租户 ID。
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 用户 ID。
     */
    public static final String USER_ID = "userId";

    /**
     * 应用名。
     */
    public static final String APP_NAME = "appName";

    /**
     * 当前时间戳（ISO-8601），可用于 GT/LT 比较。
     */
    public static final String NOW = "now";

    /**
     * 当天日期（yyyy-MM-dd）。
     */
    public static final String DATE = "date";

    /**
     * 当天时刻（HH:mm:ss），配合 BETWEEN 做办公时间限制。
     */
    public static final String TIME = "time";

    /**
     * 小时（0-23）。
     */
    public static final String HOUR = "hour";

    /**
     * 星期（MONDAY…SUNDAY）。
     */
    public static final String DAY_OF_WEEK = "dayOfWeek";

    private AccessEnvironment() {
    }
}
