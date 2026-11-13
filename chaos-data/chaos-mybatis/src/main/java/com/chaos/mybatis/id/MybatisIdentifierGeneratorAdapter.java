package com.chaos.mybatis.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.chaos.core.id.IdGenerator;
import java.util.Objects;

/** 将应用自定义发号策略用于 MyBatis-Plus ASSIGN_ID，不创建第二个生成器。 */
public final class MybatisIdentifierGeneratorAdapter implements IdentifierGenerator {
    private final IdGenerator delegate;

    public MybatisIdentifierGeneratorAdapter(IdGenerator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Long nextId(Object entity) {
        return delegate.nextId();
    }
}
