package com.michael.chaos.core.metrics;

/**
 * 框架治理指标上报端口。
 *
 * <p>框架替业务做了大量治理决策——限流、幂等、租户准入、权限校验、token 撤销、消息投递——
 * 但这些决策此前只写日志。线上出现"下单成功率下跌"时，无法回答究竟是被限流挡了、被幂等挡了，
 * 还是租户被停用，只能翻日志。本端口把这些决策变成可聚合的计数。</p>
 *
 * <p>定义在 chaos-core 而不是 chaos-trace：限流在 chaos-web/chaos-gateway、幂等在 chaos-web、
 * 租户在 chaos-tenant、消息在 chaos-mq，这些模块并非都依赖 chaos-trace，但都依赖 chaos-core。
 * Micrometer 实现见 {@code com.michael.chaos.trace.monitor.MicrometerChaosMetrics}。</p>
 *
 * <p>实现必须满足：调用方在请求路径上同步调用，因此不得阻塞、不得抛异常。
 * 没有 Micrometer 时使用 {@link NoopChaosMetrics}，调用点无需判空。</p>
 */
@FunctionalInterface
public interface ChaosMetrics {

    /**
     * 计数加一。
     *
     * @param name 指标名，取自 {@link ChaosMeterNames}
     * @param tags 标签，按 {@code key1, value1, key2, value2} 顺序成对传入；
     *        只允许低基数取值（原因、结果、维度名），不得传入用户 ID、租户 ID、幂等 key 等高基数值，
     *        否则会把时序库打爆
     */
    void increment(String name, String... tags);
}
