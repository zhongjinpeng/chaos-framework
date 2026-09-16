package com.michael.chaos.mybatis.datascope;

import java.util.Optional;

/**
 * 默认空数据权限提供者。
 */
public class NoopDataScopeProvider implements DataScopeProvider {

    /**
     * 默认不追加数据权限条件。
     */
    @Override
    public Optional<DataScopeCondition> condition(String scope) {
        return Optional.empty();
    }
}
