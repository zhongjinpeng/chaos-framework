package com.michael.chaos.security.api.access;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 给动态策略来源加本地缓存与降级（装饰器）。
 *
 * <p>远端来源（数据库、Redis、配置中心）不能每次授权判定都查一次，因此在 TTL 内复用上一次结果。</p>
 *
 * <p>拉取失败时的行为经过取舍：</p>
 * <ul>
 *   <li>已有旧快照 —— 继续使用旧快照并顺延一个 TTL 后重试。远端抖动或有人把策略写坏，不会让所有 DENY 策略
 *   凭空消失（静默放行），也不会让线上请求直接 500；失败通过 failureHandler 上报日志。</li>
 *   <li>没有旧快照（启动后第一次就失败）—— 原样抛出异常。此时既没有策略也没有历史，继续跑等于无策略放行，
 *   不如让调用方感知。</li>
 * </ul>
 */
public class CachingAuthorizationPolicySource implements AuthorizationPolicySource {

    private final AuthorizationPolicySource delegate;

    private final long ttlMillis;

    private final LongSupplier clock;

    private final Consumer<RuntimeException> failureHandler;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    /**
     * 创建带缓存的策略来源。
     *
     * @param delegate 实际来源
     * @param ttl 缓存有效期，为 null 或非正数时不缓存
     */
    public CachingAuthorizationPolicySource(AuthorizationPolicySource delegate, Duration ttl) {
        this(delegate, ttl, System::currentTimeMillis, failure -> {
        });
    }

    /**
     * 创建带缓存的策略来源，并指定拉取失败的处理方式（通常是打日志）。
     */
    public CachingAuthorizationPolicySource(
            AuthorizationPolicySource delegate,
            Duration ttl,
            Consumer<RuntimeException> failureHandler) {
        this(delegate, ttl, System::currentTimeMillis, failureHandler);
    }

    /**
     * 创建带缓存的策略来源，并指定时钟（便于测试）。
     */
    public CachingAuthorizationPolicySource(
            AuthorizationPolicySource delegate,
            Duration ttl,
            LongSupplier clock,
            Consumer<RuntimeException> failureHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ttlMillis = ttl == null || ttl.isNegative() ? 0L : ttl.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.failureHandler = failureHandler == null ? failure -> {
        } : failureHandler;
    }

    @Override
    public List<AuthorizationPolicy> policies() {
        long now = clock.getAsLong();
        Snapshot current = snapshot.get();
        if (current != null && now < current.expiresAt()) {
            return current.policies();
        }
        try {
            List<AuthorizationPolicy> loaded = load();
            snapshot.set(new Snapshot(loaded, now + ttlMillis));
            return loaded;
        } catch (RuntimeException ex) {
            if (current == null) {
                throw ex;
            }
            failureHandler.accept(ex);
            snapshot.set(new Snapshot(current.policies(), now + ttlMillis));
            return current.policies();
        }
    }

    /**
     * 丢弃缓存，下次取策略时重新拉取。
     */
    public void refresh() {
        snapshot.set(null);
    }

    /**
     * 当前缓存中的策略数量；尚未拉取过时返回 0。
     */
    public int cachedSize() {
        Snapshot current = snapshot.get();
        return current == null ? 0 : current.policies().size();
    }

    private List<AuthorizationPolicy> load() {
        List<AuthorizationPolicy> policies = delegate.policies();
        return policies == null ? List.of() : List.copyOf(policies);
    }

    private record Snapshot(List<AuthorizationPolicy> policies, long expiresAt) {
    }
}
