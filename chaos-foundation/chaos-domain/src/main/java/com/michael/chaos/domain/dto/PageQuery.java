package com.michael.chaos.domain.dto;

/**
 * 标准分页查询参数。
 *
 * <p>页码和每页大小都有上限：深分页（{@code pageNo} 极大）会让数据库扫描并丢弃海量行，
 * 攻击者只需构造 {@code pageNo=100000000} 即可拖垮数据库。需要遍历全量数据时应使用游标（按主键 seek）分页。</p>
 *
 * @param pageNo 页码，从 1 开始，超过 {@link #MAX_PAGE_NO} 时会被截断
 * @param pageSize 每页记录数，超过 {@link #MAX_PAGE_SIZE} 时会被截断
 */
public record PageQuery(long pageNo, long pageSize) {

    /**
     * 允许的最大页码。
     */
    public static final long MAX_PAGE_NO = 10_000L;

    /**
     * 允许的最大每页记录数。
     */
    public static final long MAX_PAGE_SIZE = 500L;

    private static final long DEFAULT_PAGE_NO = 1L;

    private static final long DEFAULT_PAGE_SIZE = 20L;

    /**
     * 规范化分页参数，避免业务层重复处理非法页码、过大的分页大小和深分页。
     */
    public PageQuery {
        pageNo = Math.min(Math.max(pageNo, DEFAULT_PAGE_NO), MAX_PAGE_NO);
        pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 返回数据库查询偏移量，最大为 {@code (MAX_PAGE_NO - 1) * MAX_PAGE_SIZE}，不会溢出。
     */
    public long offset() {
        return (pageNo - 1) * pageSize;
    }
}
