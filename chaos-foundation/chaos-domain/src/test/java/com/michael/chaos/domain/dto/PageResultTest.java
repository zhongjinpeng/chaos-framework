package com.michael.chaos.domain.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void constructorAndAccessors() {
        List<String> records = List.of("a", "b", "c");
        PageResult<String> result = new PageResult<>(records, 100L, 1L, 10L);

        assertEquals(List.of("a", "b", "c"), result.records());
        assertEquals(100L, result.total());
        assertEquals(1L, result.pageNo());
        assertEquals(10L, result.pageSize());
    }

    @Test
    void recordsAreDefensivelyCopied() {
        ArrayList<String> mutableList = new ArrayList<>(List.of("x", "y"));
        PageResult<String> result = new PageResult<>(mutableList, 2L, 1L, 10L);

        // Mutating the original list should not affect the PageResult
        mutableList.add("z");
        assertEquals(2, result.records().size());
        assertEquals(List.of("x", "y"), result.records());
    }

    @Test
    void recordsListIsUnmodifiable() {
        PageResult<String> result = new PageResult<>(List.of("a"), 1L, 1L, 10L);

        assertThrows(UnsupportedOperationException.class, () -> result.records().add("b"));
    }

    @Test
    void emptyRecords() {
        PageResult<Integer> result = new PageResult<>(List.of(), 0L, 1L, 20L);

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
        assertEquals(1L, result.pageNo());
        assertEquals(20L, result.pageSize());
    }

    @Test
    void equalsAndHashCode() {
        PageResult<String> a = new PageResult<>(List.of("a", "b"), 10L, 1L, 5L);
        PageResult<String> b = new PageResult<>(List.of("a", "b"), 10L, 1L, 5L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

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
