package com.chaos.mybatis.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chaos.domain.dto.PageResult;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Repository 和 QueryAdapter 共用的 MyBatis 分页结果适配，不执行查询或加载关联。
 * 保留数据库分页的实际页码、条数和总数，避免各持久化适配器重复复制元数据。
 */
public final class PageResults {
    private PageResults() {}

    /**
     * 转换当前页记录并保留分页元数据，不修改原分页对象。
     * @param page 已完成查询的非 null MyBatis 分页结果
     * @param mapper 无额外查询的记录转换函数；关联数据应由调用方预先批量加载
     * @param <S> 持久化记录类型
     * @param <T> 返回记录类型
     * @return 不暴露 MyBatis 类型的领域分页结果
     */
    public static <S, T> PageResult<T> from(IPage<S> page, Function<? super S, ? extends T> mapper) {
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        return withRecords(page, page.getRecords().stream().<T>map(mapper).toList());
    }

    /**
     * 使用已完成整页关联装配的记录，保留原查询的分页元数据。
     * @param page 非 null 的分页元数据来源，不重新计数或更改当前页
     * @param records 调用方已经转换的当前页记录，必须非 null
     * @param <T> 返回记录类型
     * @return 对记录列表防御性复制的分页结果
     */
    public static <T> PageResult<T> withRecords(IPage<?> page, List<T> records) {
        Objects.requireNonNull(page, "page must not be null");
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
