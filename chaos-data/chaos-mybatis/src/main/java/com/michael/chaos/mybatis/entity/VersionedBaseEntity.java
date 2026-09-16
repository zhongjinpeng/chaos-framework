package com.michael.chaos.mybatis.entity;

import com.baomidou.mybatisplus.annotation.Version;

/**
 * 带乐观锁版本号的持久化基础实体。
 *
 * <p>没有把 {@code @Version} 直接加到 {@link BaseEntity}：已有表没有 {@code version} 列，升级后所有 SQL 都会失败。
 * 需要乐观锁的实体改为继承本类，并在表中增加 {@code version bigint not null default 0}。
 * 更新时 MyBatis Plus 会追加 {@code where version = ?}，并发更新失败返回影响行数 0。</p>
 */
public abstract class VersionedBaseEntity extends BaseEntity {

    /**
     * 乐观锁版本号。
     */
    @Version
    private Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
