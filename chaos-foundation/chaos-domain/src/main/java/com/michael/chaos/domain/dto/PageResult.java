package com.michael.chaos.domain.dto;

import java.util.List;

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
}
