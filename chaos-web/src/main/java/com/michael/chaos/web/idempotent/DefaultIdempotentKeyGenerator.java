package com.michael.chaos.web.idempotent;

/**
 * 默认幂等 key 生成策略。
 *
 * <p>生成格式：{@code idem:{method}:{path}:t={tenantId}:u={userId}:{key}}。</p>
 *
 * <ul>
 *     <li>按租户、用户、方法和路径隔离：避免 A 用户使用的 {@code Idempotency-Key} 挡住 B 用户的请求，
 *     也避免注解上的固定 key 退化为全局锁。</li>
 *     <li>不再使用 traceId 兜底：traceId 每个请求都会重新生成，拼进 key 等于没有幂等保护。
 *     调用方既未提供固定 key 也未提供请求 key 时返回空字符串，由适配层决定拒绝或放行。</li>
 * </ul>
 */
public class DefaultIdempotentKeyGenerator implements IdempotentKeyGenerator {

    /**
     * 生成的 key 前缀，方便在 Redis 中按前缀排查和清理。
     */
    public static final String KEY_PREFIX = "idem:";

    /**
     * 请求边界传入 key 的最大长度，防止超长 key 占用存储。
     */
    public static final int MAX_REQUEST_KEY_LENGTH = 128;

    /**
     * 优先使用固定 key，其次使用请求边界传入的 key；两者都缺失时返回空字符串。
     */
    @Override
    public String generate(IdempotentKeyContext context) {
        String rawKey = !context.fixedKey().isBlank() ? context.fixedKey().trim() : normalizeRequestKey(context.requestKey());
        if (rawKey.isEmpty()) {
            return "";
        }
        return KEY_PREFIX
                + context.method()
                + ":" + context.path()
                + ":t=" + context.tenantId()
                + ":u=" + context.userId()
                + ":" + rawKey;
    }

    /**
     * 请求 key 来自外部，只接受可打印 ASCII 且限制长度，非法值视为缺失。
     */
    private static String normalizeRequestKey(String requestKey) {
        String trimmed = requestKey == null ? "" : requestKey.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REQUEST_KEY_LENGTH) {
            return "";
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 0x21 || ch > 0x7E) {
                return "";
            }
        }
        return trimmed;
    }
}
