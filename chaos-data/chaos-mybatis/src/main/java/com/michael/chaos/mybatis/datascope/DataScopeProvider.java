package com.michael.chaos.mybatis.datascope;

import com.michael.chaos.security.api.datascope.DataScopeRequest;
import java.util.Optional;

/**
 * 数据权限条件提供者。
 */
public interface DataScopeProvider {

    /**
     * 根据策略编码返回当前用户的数据权限条件。
     */
    Optional<DataScopeCondition> condition(String scope);

    /**
     * 根据完整数据权限请求返回当前用户的数据权限条件。
     */
    default Optional<DataScopeCondition> condition(DataScopeRequest request) {
        return condition(request == null ? "" : request.scope());
    }
}
