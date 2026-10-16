package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelImportsRowProcessingTest {

    @Test
    void shouldMapRowsInOrder() {
        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");

        ExcelImports.Data<Integer> data = ExcelImports.processRequired(
                file, ignored -> List.of("A", "BB"), 2, String::length);

        assertThat(data.rows()).containsExactly(1, 2);
    }

    @Test
    void shouldProcessRowsInOrder() {
        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");
        List<String> processed = new ArrayList<>();

        ExcelImports.processRequired(
                file, ignored -> List.of("A", "B"), 2, row -> {
                    processed.add(row);
                    return row;
                });

        assertThat(processed).containsExactly("A", "B");
    }

    @Test
    void shouldWrapFailureWithExcelRowNumberAndOriginalCause() {
        IllegalArgumentException cause = new IllegalArgumentException("invalid value");

        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");

        assertThatThrownBy(() -> ExcelImports.processRequired(
                file,
                ignored -> List.of("valid", "invalid"),
                2,
                row -> {
                    if (row.equals("invalid")) {
                        throw cause;
                    }
                    return row;
                }))
                .isInstanceOfSatisfying(ExcelImports.RowProcessingException.class, exception -> {
                    assertThat(exception.rowNumber()).isEqualTo(3);
                    assertThat(exception.originalCause()).isSameAs(cause);
                });
    }
}
