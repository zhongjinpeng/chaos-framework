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
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTemplateFeaturesTest {

    @Test
    void shouldCreateSeparateSampleSheetsDropdownsAndInstructions() throws Exception {
        TemplateSheet reagents = TemplateSheet.builder("试剂", TemplateFeatureRow.class)
                .dropdown("units", List.of("g", "mL"))
                .samples(List.of(new TemplateFeatureRow(
                        "氯化钠", "g", LocalDate.of(2027, 1, 1))))
                .instructions("按列填写，单位从下拉列表选择")
                .build();

        ExcelDocument template = ExcelTemplates.write("试剂模板", List.of(reagents),
                TemplateOptions.defaults()
                        .samplePlacement(SamplePlacement.SEPARATE_SHEET)
                        .includeInstructions(true));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(template.content()))) {
            assertThat(workbook.getSheet("试剂")).isNotNull();
            assertThat(workbook.getSheet("示例-试剂")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_chaos_dict"))).isTrue();

            Sheet data = workbook.getSheet("试剂");
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo("名称 *");
            assertThat(data.getRow(0).getCell(1).getStringCellValue()).isEqualTo("单位 *");
            assertThat(data.getDataValidations()).singleElement().satisfies(validation -> {
                assertThat(validation.getValidationConstraint().getFormula1())
                        .startsWith("chaos_dd_units_");
                assertThat(validation.getRegions().getCellRangeAddresses())
                        .singleElement()
                        .satisfies(range -> {
                            assertThat(range.getFirstRow()).isEqualTo(1);
                            assertThat(range.getLastRow()).isEqualTo(4999);
                            assertThat(range.getFirstColumn()).isEqualTo(1);
                            assertThat(range.getLastColumn()).isEqualTo(1);
                        });
            });

            Sheet samples = workbook.getSheet("示例-试剂");
            assertThat(samples.getRow(1).getCell(0).getStringCellValue()).isEqualTo("氯化钠");
            assertThat(samples.getRow(1).getCell(1).getStringCellValue()).isEqualTo("g");
        }
    }

    @Test
    void shouldValidateHeadersAndIgnoreSampleSheetsDuringImport() throws Exception {
        TemplateSheet reagents = TemplateSheet.builder("试剂", TemplateFeatureRow.class)
                .dropdown("units", List.of("g", "mL"))
                .samples(List.of(new TemplateFeatureRow(
                        "氯化钠", "g", LocalDate.of(2027, 1, 1))))
                .build();
        ExcelDocument template = ExcelTemplates.write("试剂模板", List.of(reagents),
                TemplateOptions.defaults().samplePlacement(SamplePlacement.SEPARATE_SHEET));

        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("试剂", TemplateFeatureRow.class)
                        .requireData(false)
                        .build())
                .build();

        try (ExcelImports.StagedFile staged = ExcelImports.stage(
                template.filename(), new ByteArrayInputStream(template.content()),
                ExcelImports.Options.xlsx(template.content().length))) {
            ImportResult result = ExcelImports.execute(staged, plan);
            assertThat(result.totalRows()).isZero();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.sheets()).containsOnlyKeys("试剂");
        }
    }


    @Test
    void shouldCreateMultipleDataSheetsWithIndependentSamplesAndDropdowns() throws Exception {
        TemplateSheet reagents = TemplateSheet.builder("试剂", TemplateFeatureRow.class)
                .dropdown("units", List.of("g", "mL"))
                .samples(List.of(new TemplateFeatureRow(
                        "氯化钠", "g", LocalDate.of(2027, 1, 1))))
                .instructions("按列填写")
                .build();
        TemplateSheet instruments = TemplateSheet.builder("仪器", InstrumentRow.class)
                .dropdown("instrument_units", List.of("台", "套"))
                .samples(List.of(new InstrumentRow("电子天平", "台")))
                .build();

        ExcelDocument template = ExcelTemplates.write("实验室模板", List.of(reagents, instruments),
                TemplateOptions.defaults()
                        .samplePlacement(SamplePlacement.SEPARATE_SHEET)
                        .includeInstructions(true));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(template.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            assertThat(workbook.getSheet("试剂")).isNotNull();
            assertThat(workbook.getSheet("仪器")).isNotNull();
            assertThat(workbook.getSheet("示例-试剂")).isNotNull();
            assertThat(workbook.getSheet("示例-仪器")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_chaos_dict"))).isTrue();

            assertThat(workbook.getSheet("试剂").getDataValidations()).singleElement()
                    .satisfies(validation -> assertThat(
                            validation.getValidationConstraint().getFormula1())
                            .startsWith("chaos_dd_units_"));
            assertThat(workbook.getSheet("仪器").getDataValidations()).singleElement()
                    .satisfies(validation -> assertThat(
                            validation.getValidationConstraint().getFormula1())
                            .startsWith("chaos_dd_instrument_units_"));
            assertThat(workbook.getSheet("示例-仪器").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("电子天平");
        }

        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("试剂", TemplateFeatureRow.class)
                        .requireData(false)
                        .build())
                .sheet(SheetImportSpec.builder("仪器", InstrumentRow.class)
                        .requireData(false)
                        .build())
                .build();
        try (ExcelImports.StagedFile staged = ExcelImports.stage(
                template.filename(), new ByteArrayInputStream(template.content()),
                ExcelImports.Options.xlsx(template.content().length))) {
            ImportResult result = ExcelImports.execute(staged, plan);
            assertThat(result.totalRows()).isZero();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.sheets()).containsOnlyKeys("试剂", "仪器");
        }
    }

    public static class TemplateFeatureRow {
        @ExcelColumn(name = "名称", index = 0, required = true, width = 24)
        private String name;
        @ExcelColumn(name = "单位", index = 1, required = true, dropdown = "units")
        private String unit;
        @ExcelColumn(name = "日期", index = 2, format = "yyyy-MM-dd", width = 16)
        private LocalDate date;

        public TemplateFeatureRow() {
        }

        TemplateFeatureRow(String name, String unit, LocalDate date) {
            this.name = name;
            this.unit = unit;
            this.date = date;
        }
    }

    public static class InstrumentRow {
        @ExcelColumn(name = "名称", index = 0, required = true, width = 24)
        private String name;
        @ExcelColumn(name = "单位", index = 1, required = true, dropdown = "instrument_units")
        private String unit;

        public InstrumentRow() {
        }

        InstrumentRow(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }
    }
}
