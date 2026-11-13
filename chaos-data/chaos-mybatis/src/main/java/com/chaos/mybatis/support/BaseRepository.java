package com.chaos.mybatis.support;

import com.chaos.core.id.IdGenerator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 需要显式分配主键的仓储基类。Spring 为所有子类注入同一 IdGenerator；
 * 手动创建仓储的测试须通过 setIdGenerator 提供测试生成器。
 */
public abstract class BaseRepository {
    private IdGenerator idGenerator;

    /**
     * 注入应用的唯一发号策略，不能为 null；不持有静态全局状态。
     * @param idGenerator 与 MyBatis-Plus ASSIGN_ID 共用的生成器
     */
    @Autowired
    public final void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    /**
     * 为本次写入预先分配主键；事务回滚允许断号。
     * @return 新的 64 位主键
     */
    protected final long nextId() {
        return Objects.requireNonNull(idGenerator, "IdGenerator has not been injected").nextId();
    }
}
