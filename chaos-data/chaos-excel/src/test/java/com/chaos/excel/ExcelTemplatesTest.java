package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.excel.annotation.ExcelColumn;
import com.chaos.excel.importer.ImportPlan;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.importer.SheetImportSpec;
import com.chaos.excel.template.SamplePlacement;
import com.chaos.excel.template.TemplateOptions;
import com.chaos.excel.template.TemplateSheet;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTemplatesTest {

    @Test
    void shouldSupportInlineSamplesAndSkipThemDuringImport() throws Exception {
        TemplateSheet sheet = TemplateSheet.builder("试剂", TemplateRow.class)
                .dropdown("units", List.of("g", "mL"))
                .samples(List.of(new TemplateRow("氯化钠", "g")))
                .build();
        ExcelDocument document = ExcelTemplates.write("试剂模板", List.of(sheet),
                TemplateOptions.defaults().samplePlacement(SamplePlacement.INLINE));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(document.content()))) {
            assertThat(document.filename()).isEqualTo("试剂模板.xlsx");
            assertThat(workbook.getSheet("试剂")).isNotNull();
            assertThat(workbook.getSheet("示例-试剂")).isNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_chaos_dict"))).isTrue();

            Sheet data = workbook.getSheet("试剂");
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo("名称 *");
            assertThat(data.getRow(1).getCell(0).getStringCellValue()).isEqualTo("氯化钠");
            assertThat(data.getDataValidations()).singleElement().satisfies(validation ->
                    assertThat(validation.getValidationConstraint().getFormula1())
                            .startsWith("chaos_dd_units_"));
        }

        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("试剂", TemplateRow.class)
                        .skipSampleRows(1)
                        .requireData(false)
                        .build())
                .build();
        try (ExcelImports.StagedFile staged = ExcelImports.stage(
                document.filename(), new ByteArrayInputStream(document.content()),
                ExcelImports.Options.xlsx(document.content().length))) {
            ImportResult result = ExcelImports.execute(staged, plan);
            assertThat(result.totalRows()).isZero();
            assertThat(result.isSuccess()).isTrue();
        }
    }

    public static class TemplateRow {
        @ExcelColumn(name = "名称", index = 0, required = true, width = 20)
        private String name;
        @ExcelColumn(name = "单位", index = 1, required = true, width = 12, dropdown = "units")
        private String unit;

        public TemplateRow() {
        }

        TemplateRow(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }
}
