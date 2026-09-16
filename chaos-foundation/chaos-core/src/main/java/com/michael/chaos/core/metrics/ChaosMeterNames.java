package com.michael.chaos.core.metrics;

/**
 * 框架治理指标名与标签名。
 *
 * <p>命名遵循 Micrometer 约定：点分小写，计数器以动作的过去分词结尾。
 * 所有指标都带 {@code framework=chaos} 公共标签（由 chaos-application-starter 的 MeterRegistryCustomizer 添加）。</p>
 */
public final class ChaosMeterNames {

    /**
     * 被限流拒绝的请求数。标签：{@link #TAG_SOURCE}（web / gateway）、{@link #TAG_DIMENSION}。
     */
    public static final String RATE_LIMIT_REJECTED = "chaos.ratelimit.rejected";

    /**
     * 限流器调用失败数（Redis 不可用等）。标签：{@link #TAG_SOURCE}、{@link #TAG_OUTCOME}（fail-open / fail-closed）。
     */
    public static final String RATE_LIMIT_ERRORS = "chaos.ratelimit.errors";

    /**
     * 被幂等拒绝的重复请求数（首次请求仍在执行中，无快照可回放）。
     */
    public static final String IDEMPOTENT_REJECTED = "chaos.idempotent.rejected";

    /**
     * 命中响应回放的重复请求数。
     */
    public static final String IDEMPOTENT_REPLAYED = "chaos.idempotent.replayed";

    /**
     * 保存响应快照失败或被跳过的次数。标签：{@link #TAG_REASON}（too-large / async / error）。
     */
    public static final String IDEMPOTENT_RECORD_SKIPPED = "chaos.idempotent.record.skipped";

    /**
     * 租户准入被拒次数。标签：{@link #TAG_SOURCE}、{@link #TAG_REASON}（租户状态或 missing）。
     */
    public static final String TENANT_DENIED = "chaos.tenant.denied";

    /**
     * 权限校验被拒次数。标签：{@link #TAG_SOURCE}（permission / data-scope）。
     */
    public static final String SECURITY_ACCESS_DENIED = "chaos.security.access.denied";

    /**
     * 认证失败次数。标签：{@link #TAG_SOURCE}（gateway / resource-server）、{@link #TAG_REASON}。
     */
    public static final String SECURITY_AUTH_FAILED = "chaos.security.auth.failed";

    /**
     * token 撤销检查命中次数（token 已被拉黑）。
     */
    public static final String SECURITY_TOKEN_REVOKED = "chaos.security.token.revoked";

    /**
     * opaque token introspection 缓存访问次数。标签：{@link #TAG_OUTCOME}（hit / miss）。
     */
    public static final String SECURITY_INTROSPECTION_CACHE = "chaos.security.introspection.cache";

    /**
     * 分布式锁获取次数。标签：{@link #TAG_OUTCOME}（acquired / missed）。
     */
    public static final String LOCK_ACQUIRE = "chaos.lock.acquire";

    /**
     * outbox 消息派发次数。标签：{@link #TAG_OUTCOME}（sent / retry / dead）。
     */
    public static final String MQ_OUTBOX_DISPATCHED = "chaos.mq.outbox.dispatched";

    /**
     * outbox 待派发消息数（gauge，由自动装配注册）。
     */
    public static final String MQ_OUTBOX_PENDING = "chaos.mq.outbox.pending";

    /**
     * 审计事件投递次数。标签：{@link #TAG_OUTCOME}（published / dropped）。
     */
    public static final String AUDIT_EVENTS = "chaos.audit.events";

    /**
     * HTTP 服务端请求指标名（Spring Boot 内置，列在这里便于统一引用）。
     */
    public static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    /**
     * 产生该事件的组件。
     */
    public static final String TAG_SOURCE = "source";

    /**
     * 结果。
     */
    public static final String TAG_OUTCOME = "outcome";

    /**
     * 原因。取值必须来自有限枚举，不得使用异常消息等自由文本。
     */
    public static final String TAG_REASON = "reason";

    /**
     * 限流维度（ip / user / tenant / path / route）。
     */
    public static final String TAG_DIMENSION = "dimension";

    private ChaosMeterNames() {
    }
}
