package com.michael.chaos.security.api.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组装授权决策服务的构造器。
 *
 * <p>网关侧用它一次性组装出 {@link AuthorizationManager}：网关只依赖 chaos-security-api，没有
 * 资源服务器那一套可逐个覆盖的 Bean，把拼装规则散在自动装配里容易和资源服务器走偏。
 * 资源服务器仍然逐个注册 RBAC 策略、配置策略组、决策服务，方便业务单独覆盖其中一项。</p>
 *
 * <p>拼装结果固定为：配置/动态策略在前、RBAC 在后，顶层用拒绝优先合并——
 * 配置里的 DENY 策略因此总能否决 RBAC 的放行，{@code combiningAlgorithm} 只作用于配置策略之间。</p>
 */
public class AuthorizationManagerBuilder {

    private String policyGroupId = "chaos-configured-access";

    private Set<String> adminRoles = Set.of("admin");

    private RoleHierarchy roleHierarchy = RoleHierarchy.none();

    private boolean wildcardPermissionEnabled = true;

    private PolicyCombiningAlgorithm combiningAlgorithm = PolicyCombiningAlgorithm.DENY_OVERRIDES;

    private final List<AuthorizationPolicy> policies = new ArrayList<>();

    private AuthorizationPolicySource policySource;

    /**
     * 创建构造器。
     */
    public static AuthorizationManagerBuilder create() {
        return new AuthorizationManagerBuilder();
    }

    /**
     * 设置配置策略分组 ID，出现在启动报告与诊断信息中。
     */
    public AuthorizationManagerBuilder policyGroupId(String policyGroupId) {
        this.policyGroupId = policyGroupId == null || policyGroupId.isBlank() ? this.policyGroupId : policyGroupId;
        return this;
    }

    /**
     * 设置命中即放行的管理员角色。
     */
    public AuthorizationManagerBuilder adminRoles(Collection<String> adminRoles) {
        this.adminRoles = AccessCollections.copyStrings(adminRoles);
        return this;
    }

    /**
     * 设置角色继承关系。
     */
    public AuthorizationManagerBuilder roleHierarchy(Map<String, ? extends Collection<String>> roleHierarchy) {
        this.roleHierarchy = new MapRoleHierarchy(roleHierarchy);
        return this;
    }

    /**
     * 设置角色继承关系。
     */
    public AuthorizationManagerBuilder roleHierarchy(RoleHierarchy roleHierarchy) {
        this.roleHierarchy = roleHierarchy == null ? RoleHierarchy.none() : roleHierarchy;
        return this;
    }

    /**
     * 是否允许权限编码通配。
     */
    public AuthorizationManagerBuilder wildcardPermissionEnabled(boolean wildcardPermissionEnabled) {
        this.wildcardPermissionEnabled = wildcardPermissionEnabled;
        return this;
    }

    /**
     * 设置配置策略之间的合并算法。
     */
    public AuthorizationManagerBuilder combiningAlgorithm(PolicyCombiningAlgorithm combiningAlgorithm) {
        this.combiningAlgorithm = combiningAlgorithm == null
                ? PolicyCombiningAlgorithm.DENY_OVERRIDES
                : combiningAlgorithm;
        return this;
    }

    /**
     * 添加配置形态的策略定义。
     *
     * @param definitions 策略定义
     * @param configPath 配置路径前缀，出错时写进诊断信息
     */
    public AuthorizationManagerBuilder policyDefinitions(List<PolicyDefinition> definitions, String configPath) {
        policies.addAll(AccessPolicyFactory.create(definitions, configPath));
        return this;
    }

    /**
     * 添加已构造好的策略。
     */
    public AuthorizationManagerBuilder policies(Collection<AuthorizationPolicy> policies) {
        if (policies != null) {
            policies.stream().filter(policy -> policy != null).forEach(this.policies::add);
        }
        return this;
    }

    /**
     * 设置动态策略来源，每次决策都会重新读取。
     */
    public AuthorizationManagerBuilder policySource(AuthorizationPolicySource policySource) {
        this.policySource = policySource;
        return this;
    }

    /**
     * 构造授权决策服务。
     */
    public AuthorizationManager build() {
        List<AuthorizationPolicy> chain = new ArrayList<>();
        chain.add(new CompositeAuthorizationPolicy(policyGroupId, policies, combiningAlgorithm));
        if (policySource != null) {
            chain.add(new CompositeAuthorizationPolicy(policyGroupId + "-dynamic", policySource, combiningAlgorithm));
        }
        chain.add(new RbacAuthorizationPolicy("rbac", adminRoles, roleHierarchy, wildcardPermissionEnabled));
        return new DefaultAuthorizationManager(chain, PolicyCombiningAlgorithm.DENY_OVERRIDES);
    }
}
