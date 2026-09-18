package com.michael.chaos.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    /**
     * 分页结果是对外契约，字段读写必须原样：漏一个字段前端就少一段数据。
     */
    @Test
    void constructorAndAccessors() {
        List<String> records = List.of("a", "b", "c");
        PageResult<String> result = new PageResult<>(records, 100L, 1L, 10L);

        assertEquals(List.of("a", "b", "c"), result.records());
        assertEquals(100L, result.total());
        assertEquals(1L, result.pageNo());
        assertEquals(10L, result.pageSize());
    }

    /**
     * 构造后修改传入的列表不能影响结果对象，避免调用方复用集合导致响应被改写。
     */
    @Test
    void recordsAreDefensivelyCopied() {
        ArrayList<String> mutableList = new ArrayList<>(List.of("x", "y"));
        PageResult<String> result = new PageResult<>(mutableList, 2L, 1L, 10L);

        // Mutating the original list should not affect the PageResult
        mutableList.add("z");
        assertEquals(2, result.records().size());
        assertEquals(List.of("x", "y"), result.records());
    }

    /**
     * 返回的列表不可变，防止上层往结果里追加数据绕过分页语义。
     */
    @Test
    void recordsListIsUnmodifiable() {
        PageResult<String> result = new PageResult<>(List.of("a"), 1L, 1L, 10L);

        assertThrows(UnsupportedOperationException.class, () -> result.records().add("b"));
    }

    /**
     * 空结果要是合法状态：没有数据时返回空列表和 0，而不是 null。
     */
    @Test
    void emptyRecords() {
        PageResult<Integer> result = new PageResult<>(List.of(), 0L, 1L, 20L);

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
        assertEquals(1L, result.pageNo());
        assertEquals(20L, result.pageSize());
    }

    /**
     * 分页结果会被放进缓存与断言，值语义必须正确。
     */
    @Test
    void equalsAndHashCode() {
        PageResult<String> a = new PageResult<>(List.of("a", "b"), 10L, 1L, 5L);
        PageResult<String> b = new PageResult<>(List.of("a", "b"), 10L, 1L, 5L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * 日志里打印分页结果时要能看出实际内容，便于排查分页参数问题。
     */
    @Test
    void toStringContainsFields() {
        PageResult<String> result = new PageResult<>(List.of("item"), 50L, 3L, 10L);
        String str = result.toString();

        assertTrue(str.contains("item"));
        assertTrue(str.contains("50"));
        assertTrue(str.contains("3"));
        assertTrue(str.contains("10"));
    }
}
