package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.excel.ExcelFacadesTest.TestRow;
import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;
import com.chaos.excel.template.TemplateOptions;
import com.chaos.excel.template.TemplateSheet;
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

    @Test
    void shouldListSheetNamesInWorkbookOrder() throws Exception {
        ExcelDocument document = ExcelExports.write("multi", List.of(
                ExportSheet.of("主表", TestRow.class, List.of(List.of(new TestRow("A", "一室", 1)))),
                ExportSheet.of("明细", TestRow.class, List.of(List.of(new TestRow("B", "二室", 2))))),
                ExportOptions.defaults());
        byte[] content = document.content();
        try (ExcelImports.StagedFile file = ExcelImports.stage(
                document.filename(), new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length))) {
            assertThat(ExcelImports.sheetNames(file)).containsExactly("主表", "明细");
        }
    }

    @Test
    void shouldSkipHiddenDictionarySheetWhenListingVisibleNames() throws Exception {
        ExcelDocument document = ExcelTemplates.write("template",
                List.of(TemplateSheet.builder("数据", ExcelTemplatesTest.TemplateRow.class)
                        .dropdown("units", List.of("g", "mL"))
                        .build()),
                TemplateOptions.defaults());
        byte[] content = document.content();
        try (ExcelImports.StagedFile file = ExcelImports.stage(
                document.filename(), new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length))) {
            assertThat(ExcelImports.sheetNames(file)).containsExactly("数据", "_chaos_dict");
            assertThat(ExcelImports.visibleSheetNames(file)).containsExactly("数据");
        }
    }

    @Test
    void shouldRejectSheetNamesForClosedStagedFile() throws Exception {
        ExcelImports.StagedFile file = stagedFile(List.of());
        file.close();

        assertThatThrownBy(() -> ExcelImports.sheetNames(file))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("already closed");
    }

    private static ExcelImports.StagedFile stagedFile(List<TestRow> rows) {
        ExcelDocument document = ExcelExports.write("records", TestRow.class, rows,
                new ExcelExports.WriteOptions("记录", true));
        byte[] content = document.content();
        return ExcelImports.stage(document.filename(), new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length));
    }
}
