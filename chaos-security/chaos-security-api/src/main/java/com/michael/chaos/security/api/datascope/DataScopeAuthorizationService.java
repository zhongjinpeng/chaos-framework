package com.michael.chaos.security.api.datascope;

import com.michael.chaos.security.api.access.AuthorizationDecision;
import com.michael.chaos.security.api.access.AuthorizationManager;
import java.util.Objects;

/**
 * 数据权限授权服务。
 *
 * <p>该服务只负责把数据权限请求转换为通用 RBAC/ABAC 授权请求；SQL 条件仍由
 * MyBatis 侧 DataScopeProvider 生成。</p>
 */
public class DataScopeAuthorizationService {

    private static final String ACTION_PREFIX = "data-scope:";

    private final AuthorizationManager authorizationManager;

    /**
     * 创建数据权限授权服务。
     */
    public DataScopeAuthorizationService(AuthorizationManager authorizationManager) {
        this.authorizationManager = Objects.requireNonNull(authorizationManager, "authorizationManager must not be null");
    }

    /**
     * 对数据权限请求做授权决策。
     */
    public AuthorizationDecision decide(DataScopeRequest request) {
        DataScopeRequest normalized = request == null ? DataScopeRequest.of("") : request;
        return authorizationManager.decide(normalized.toAuthorizationRequest(action(normalized.scope())));
    }

    /**
     * 判断是否允许使用该数据权限范围。
     */
    public boolean isAllowed(DataScopeRequest request) {
        return decide(request).allowed();
    }

    private String action(String scope) {
        return ACTION_PREFIX + Objects.requireNonNullElse(scope, "").trim();
    }
}
