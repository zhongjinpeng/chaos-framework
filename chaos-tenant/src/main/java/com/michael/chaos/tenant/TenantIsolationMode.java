package com.michael.chaos.tenant;

/**
 * SaaS 租户数据隔离模式。
 */
public enum TenantIsolationMode {

    /**
     * 多租户共享数据库和共享表，通过 tenant_id 隔离。
     */
    SHARED_SCHEMA,

    /**
     * 多租户共享数据库，不同租户使用独立 schema。
     */
    SCHEMA,

    /**
     * 每个租户使用独立数据库。
     */
    DATABASE
}
