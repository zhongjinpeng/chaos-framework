package com.michael.chaos.core.metrics;

/**
 * 不上报任何指标的空实现。
 *
 * <p>没有 Micrometer 时使用，让调用点无需对 {@link ChaosMetrics} 判空——
 * 请求路径上散落的 {@code if (metrics != null)} 既噪音大，又容易漏写导致某个分支没埋点。</p>
 */
public final class NoopChaosMetrics implements ChaosMetrics {

    private static final NoopChaosMetrics INSTANCE = new NoopChaosMetrics();

    private NoopChaosMetrics() {
    }

    /**
     * 返回共享实例。
     */
    public static ChaosMetrics instance() {
        return INSTANCE;
    }

    @Override
    public void increment(String name, String... tags) {
        // 故意为空。
    }
}
