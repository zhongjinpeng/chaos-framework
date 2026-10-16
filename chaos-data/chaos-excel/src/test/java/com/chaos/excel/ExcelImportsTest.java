package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelImportsTest {

    @Test
    void shouldPrepareFileAndReadRequiredRows() {
        byte[] content = {1, 2, 3};

        ExcelImports.Data<String> data = ExcelImports.readRequired(
                "C:\\fakepath\\records.xlsx",
                content,
                ExcelImports.Options.xlsx(3),
                ignored -> List.of("A", "B"));

        assertThat(data.file().filename()).isEqualTo("records.xlsx");
        assertThat(data.rows()).containsExactly("A", "B");
    }

    @Test
    void shouldRejectWorkbookWithoutDataRows() {
        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");

        assertThatThrownBy(() -> ExcelImports.readRequired(file, ignored -> List.of()))
                .isInstanceOf(ExcelImports.NoDataException.class)
                .hasMessageContaining("at least one data row");
    }

    @Test
    void shouldReadAndProcessRows() {
        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");

        ExcelImports.Data<Integer> data = ExcelImports.processRequired(
                file, ignored -> List.of("A", "BB"), 2, String::length);

        assertThat(data.file()).isSameAs(file);
        assertThat(data.rows()).containsExactly(1, 2);
    }

    @Test
    void shouldPreserveExcelRowNumberWhenProcessingFails() {
        ExcelImports.File file = new ExcelImports.File("records.xlsx", new byte[] {1}, "hash");

        assertThatThrownBy(() -> ExcelImports.processRequired(
                        file,
                        ignored -> List.of("valid", "invalid"),
                        2,
                        row -> {
                            if (row.equals("invalid")) {
                                throw new IllegalArgumentException("invalid row");
                            }
                            return row;
                        }))
                .isInstanceOfSatisfying(
                        ExcelImports.RowProcessingException.class,
                        exception -> {
                            assertThat(exception.rowNumber()).isEqualTo(3);
                            assertThat(exception.originalCause())
                                    .isInstanceOf(IllegalArgumentException.class)
                                    .hasMessage("invalid row");
                        });
    }
}
