package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.excel.ExcelFacadesTest.TestRow;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelImportsTest {

    @Test
    void shouldReadRequiredRowsFromStagedFile() throws Exception {
        try (ExcelImports.StagedFile file = stagedFile(List.of(
                new TestRow("A", "一室", 1), new TestRow("B", "二室", 2)))) {
            ExcelImports.StagedData<TestRow> data = ExcelImports.readRequired(
                    file, TestRow.class, ExcelImports.ReadOptions.defaults());

            assertThat(data.file().filename()).isEqualTo("records.xlsx");
            assertThat(data.rows()).hasSize(2);
        }
    }

    @Test
    void shouldRejectWorkbookWithoutDataRows() throws Exception {
        try (ExcelImports.StagedFile file = stagedFile(List.of())) {
            assertThatThrownBy(() -> ExcelImports.readRequired(
                    file, TestRow.class, ExcelImports.ReadOptions.defaults()))
                    .isInstanceOf(ExcelImports.NoDataException.class)
                    .hasMessageContaining("at least one data row");
        }
    }

    private static ExcelImports.StagedFile stagedFile(List<TestRow> rows) {
        ExcelDocument document = ExcelExports.write("records", TestRow.class, rows,
                new ExcelExports.WriteOptions("记录", true));
        byte[] content = document.content();
        return ExcelImports.stage(document.filename(), new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length));
    }
}
