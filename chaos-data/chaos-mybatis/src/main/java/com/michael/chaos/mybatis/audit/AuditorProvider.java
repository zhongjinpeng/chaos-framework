package com.michael.chaos.mybatis.audit;

/**
 * 审计字段 createdBy / updatedBy 的当前操作人提供者。
 *
 * <p>抽象出来是为了让审计填充不再强依赖 chaos-security：旧版本在没有 security 时整个 MetaObjectHandler 不注册，
 * {@code deleted}、{@code createdAt} 都不会填充，表没有默认值时逻辑删除查询 {@code deleted = 0} 会查不到新数据。</p>
 */
@FunctionalInterface
public interface AuditorProvider {

    /**
     * 返回当前操作人 ID；无法确定时返回 {@code null} 或空字符串，对应字段不填充。
     */
    String currentAuditor();
}
