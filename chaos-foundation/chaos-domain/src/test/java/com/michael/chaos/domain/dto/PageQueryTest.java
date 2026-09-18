package com.michael.chaos.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 分页参数测试。
 */
class PageQueryTest {

    /**
     * 页码与页大小来自外部输入，负数、0、超大值都要被规范化，否则会直接打到数据库上。
     */
    @Test
    void shouldNormalizeIllegalValues() {
        PageQuery query = new PageQuery(-1, 0);

        assertEquals(1, query.pageNo());
        assertEquals(20, query.pageSize());
        assertEquals(0, query.offset());
    }

    /**
     * 深分页页码和超大页大小必须被截断，防止数据库被拖垮。
     */
    @Test
    void shouldCapDeepPagingAndPageSize() {
        PageQuery query = new PageQuery(Long.MAX_VALUE, 1_000_000);

        assertEquals(PageQuery.MAX_PAGE_NO, query.pageNo());
        assertEquals(PageQuery.MAX_PAGE_SIZE, query.pageSize());
        assertEquals((PageQuery.MAX_PAGE_NO - 1) * PageQuery.MAX_PAGE_SIZE, query.offset());
    }
}
