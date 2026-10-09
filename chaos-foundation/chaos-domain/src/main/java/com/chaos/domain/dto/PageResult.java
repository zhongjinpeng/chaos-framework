package com.chaos.domain.dto;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 标准分页查询结果。
 *
 * @param records 当前页数据
 * @param total 总记录数
 * @param pageNo 当前页码
 * @param pageSize 每页记录数
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, long pageNo, long pageSize) {

    /**
     * 防御性复制分页数据，避免响应对象创建后被外部修改。
     */
    public PageResult {
        records = List.copyOf(records);
    }

    /**
     * 转换当前页的记录类型，同时保留分页元数据。
     *
     * @param mapper 记录转换函数
     * @param <R> 目标记录类型
     * @return 转换后的分页结果
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new PageResult<>(records.stream().<R>map(mapper).toList(), total, pageNo, pageSize);
    }
}
