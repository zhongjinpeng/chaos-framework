package com.chaos.mybatis.support;

/**
 * 批量 SQL 共用的已分配技术主键行，避免每个业务模块重复定义相同的绑定参数载体。
 * 主键由应用 IdGenerator 分配；XML 通过 id 和 value 访问，不修改业务行。
 *
 * @param id 发送 SQL 前已经分配的 64 位主键
 * @param value 原始行值
 * @param <T> 原始行类型
 */
public record IdentifiedRow<T>(long id, T value) {}
