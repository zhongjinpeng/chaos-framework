package com.michael.chaos.security.api.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * opaque token introspection 成功结果的短期缓存。
 *
 * <p>资源服务器（Servlet）与网关（WebFlux）的 introspector 此前各自实现了一份完全相同的缓存逻辑。
 * 缓存策略与协议栈、Spring Security 类型无关，因此抽到这里，两侧只保留调用适配。策略：</p>
 * <ul>
 *     <li>只缓存成功结果，失败（token 无效、授权服务器异常）不缓存；</li>
 *     <li>key 为 token 的 SHA-256 摘要，内存中不保留 token 明文；</li>
 *     <li>过期时间取 {@code ttl} 与 token 自身过期时间的较小值；</li>
 *     <li>写满且全部未过期时放弃写入而不是淘汰或拒绝：缓存只是性能优化，不能影响鉴权正确性。</li>
 * </ul>
 *
 * <p>一致性说明：token 被撤销后，最多在 {@code ttl} 时间内仍可能命中缓存，对撤销实时性要求高时应把 ttl 设为 0。</p>
 *
 * @param <P> 认证主体类型
 */
public final class TokenIntrospectionCache<P> {

    private final Duration ttl;

    private final int maxSize;

    private final Clock clock;

    private final Map<String, Entry<P>> entries = new ConcurrentHashMap<>();

    /**
     * 创建缓存。
     *
     * @param ttl 缓存时长，null、0 或负数表示关闭缓存
     * @param maxSize 最大条目数
     * @param clock 时钟
     */
    public TokenIntrospectionCache(Duration ttl, int maxSize, Clock clock) {
        this.ttl = ttl == null ? Duration.ZERO : ttl;
        this.maxSize = Math.max(maxSize, 1);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 是否启用缓存。
     */
    public boolean enabled() {
        return !ttl.isZero() && !ttl.isNegative();
    }

    /**
     * 读取未过期的缓存主体；过期条目会被顺带清除。
     *
     * @param token 原始 token
     * @return 命中返回主体
     */
    public Optional<P> get(String token) {
        if (!enabled()) {
            return Optional.empty();
        }
        String key = digest(token);
        Entry<P> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isAfter(clock.instant())) {
            return Optional.of(entry.principal());
        }
        entries.remove(key, entry);
        return Optional.empty();
    }

    /**
     * 写入成功的 introspection 结果。
     *
     * @param token 原始 token
     * @param principal 认证主体
     * @param tokenExpiresAt token 自身过期时间，可为空
     */
    public void put(String token, P principal, Instant tokenExpiresAt) {
        if (!enabled() || principal == null) {
            return;
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        if (tokenExpiresAt != null && tokenExpiresAt.isBefore(expiresAt)) {
            expiresAt = tokenExpiresAt;
        }
        if (!expiresAt.isAfter(now)) {
            return;
        }
        if (entries.size() >= maxSize) {
            entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            if (entries.size() >= maxSize) {
                return;
            }
        }
        entries.put(digest(token), new Entry<>(principal, expiresAt));
    }

    /**
     * 当前条目数，仅用于测试和监控。
     */
    public int size() {
        return entries.size();
    }

    /**
     * 每线程复用一个 {@link MessageDigest}。
     *
     * <p>{@code MessageDigest.getInstance} 每次都要走 JCA provider 查找，而 {@link #get} 位于网关和资源服务器的
     * 每请求路径上——开启缓存后每个请求至少一次、未命中两次。{@code MessageDigest} 非线程安全，
     * 但 {@code digest()} 调用后会自动 reset，同一线程内可以安全复用。</p>
     */
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    });

    private static String digest(String token) {
        MessageDigest messageDigest = SHA_256.get();
        messageDigest.reset();
        return HexFormat.of().formatHex(messageDigest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private record Entry<P>(P principal, Instant expiresAt) {
    }
}
