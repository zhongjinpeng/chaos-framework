package com.michael.chaos.security.api.access;

import java.util.List;

/**
 * 授权策略来源。
 *
 * <p>默认实现来自配置文件，业务侧可以实现本接口从数据库、配置中心等动态来源加载策略；
 * {@link DefaultAuthorizationManager} 每次决策都会重新取一次策略列表，实现方负责缓存。</p>
 */
@FunctionalInterface
public interface AuthorizationPolicySource {

    /**
     * 返回当前生效的策略列表。
     */
    List<AuthorizationPolicy> policies();

    /**
     * 创建固定策略来源。
     */
    static AuthorizationPolicySource fixed(List<AuthorizationPolicy> policies) {
        List<AuthorizationPolicy> copied = policies == null ? List.of() : List.copyOf(policies);
        return () -> copied;
    }
}
