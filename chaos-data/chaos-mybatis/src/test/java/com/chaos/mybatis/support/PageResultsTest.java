package com.chaos.mybatis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PageResultsTest {
    @Test
    void mapsEachRecordOnceAndKeepsActualMetadataWithoutMutatingSource() {
        var page = new Page<Integer>(3, 2, 19);
        page.setRecords(List.of(5, 6));
        var calls = new AtomicInteger();
        var result = PageResults.from(page, value -> {
            calls.incrementAndGet();
            return "item-" + value;
        });
        assertThat(result.records()).containsExactly("item-5", "item-6");
        assertThat(result.total()).isEqualTo(19);
        assertThat(result.pageNo()).isEqualTo(3);
        assertThat(result.pageSize()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(page.getRecords()).containsExactly(5, 6);
    }

    @Test
    void emptyOutOfRangePageKeepsCountAndDoesNotInvokeMapper() {
        var page = new Page<Integer>(10, 20, 5);
        var result = PageResults.from(page, value -> { throw new AssertionError("empty page"); });
        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.pageNo()).isEqualTo(10);
        assertThat(result.pageSize()).isEqualTo(20);
    }

    @Test
    void preassembledRecordsAreCopiedAndConversionFailuresAreNotSwallowed() {
        var page = new Page<Integer>(2, 10, 15);
        page.setRecords(List.of(1));
        var records = new ArrayList<>(List.of("with relations"));
        var result = PageResults.withRecords(page, records);
        records.clear();
        assertThat(result.records()).containsExactly("with relations");
        assertThat(result.total()).isEqualTo(15);
        var failure = new IllegalStateException("invalid row");
        assertThatThrownBy(() -> PageResults.from(page, value -> { throw failure; })).isSameAs(failure);
    }
}
