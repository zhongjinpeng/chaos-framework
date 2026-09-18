package com.michael.chaos.security.api.access;

import java.util.List;
import java.util.Objects;

/**
 * 把一组策略合并成一条策略（组合模式）。
 *
 * <p>用途有两个：把配置文件里的 ABAC 策略作为整体参与顶层决策；把动态策略来源
 * （{@link AuthorizationPolicySource}）接入顶层的策略列表，而不必改动 {@link AuthorizationManager} 的装配。</p>
 *
 * <p>组内按指定算法合并，组内全部弃权时整体弃权，由顶层继续与 RBAC 等策略合并。</p>
 */
public class CompositeAuthorizationPolicy implements AuthorizationPolicy {

    private final String id;

    private final AuthorizationPolicySource source;

    private final PolicyCombiningAlgorithm combiningAlgorithm;

    /**
     * 创建固定策略的组合策略。
     *
     * @param id 策略 ID；组内命中某条策略时，决策原因里返回的是命中策略自己的 ID
     * @param policies 组内策略
     * @param combiningAlgorithm 组内合并算法，默认拒绝优先
     */
    public CompositeAuthorizationPolicy(
            String id,
            List<AuthorizationPolicy> policies,
            PolicyCombiningAlgorithm combiningAlgorithm) {
        this(id, AuthorizationPolicySource.fixed(policies), combiningAlgorithm);
    }

    /**
     * 创建从动态来源取策略的组合策略，每次决策都会重新取一次策略列表。
     *
     * @param id 策略 ID
     * @param source 策略来源
     * @param combiningAlgorithm 组内合并算法，默认拒绝优先
     */
    public CompositeAuthorizationPolicy(
            String id,
            AuthorizationPolicySource source,
            PolicyCombiningAlgorithm combiningAlgorithm) {
        this.id = Objects.requireNonNullElse(id, "composite");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.combiningAlgorithm = combiningAlgorithm == null
                ? PolicyCombiningAlgorithm.DENY_OVERRIDES
                : combiningAlgorithm;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AuthorizationDecision decide(AuthorizationRequest request) {
        List<AuthorizationPolicy> policies = policies();
        if (policies.isEmpty()) {
            return AuthorizationDecision.abstain("no policy configured");
        }
        return combiningAlgorithm.combine(policies, request);
    }

    /**
     * 当前组内策略数量，供启动报告与诊断端点展示。
     */
    public int size() {
        return policies().size();
    }

    private List<AuthorizationPolicy> policies() {
        List<AuthorizationPolicy> policies = source.policies();
        return policies == null ? List.of() : policies;
    }
}
