package com.michael.chaos.mybatis.tenant;

/**
 * 为 MyBatis Plus 多租户 SQL 改写解析当前租户 ID。
 */
public interface TenantIdProvider {

    /**
     * @return 当前租户 ID；返回空字符串表示跳过租户过滤
     */
    String currentTenantId();
}
