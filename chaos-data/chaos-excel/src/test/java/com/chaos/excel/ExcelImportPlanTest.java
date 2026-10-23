package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.excel.annotation.ExcelColumn;
import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;
import com.chaos.excel.importer.ErrorPolicy;
import com.chaos.excel.importer.HeaderMismatchException;
import com.chaos.excel.importer.ImportPlan;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.importer.RowValidationException;
import com.chaos.excel.importer.SheetImportSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportPlanTest {

    @Test
    void shouldImportMultipleSheetsWithTypedColumns() throws Exception {
        ExcelDocument document = ExcelExports.write("multi", List.of(
                ExportSheet.of("主表", MainRow.class, List.of(List.of(
                        new MainRow("A-1", 3, LocalDate.of(2027, 1, 1), true,
                                new BigDecimal("12.50"))))),
                ExportSheet.of("明细", DetailRow.class, List.of(List.of(
                        new DetailRow("A-1", "试剂", 2))))),
                ExportOptions.defaults());

        List<MainRow> mainRows = new ArrayList<>();
        List<DetailRow> detailRows = new ArrayList<>();
        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("主表", MainRow.class)
                        .handler((row, rowNumber) -> mainRows.add(row))
                        .build())
                .sheet(SheetImportSpec.builder("明细", DetailRow.class)
                        .handler((row, rowNumber) -> detailRows.add(row))
                        .build())
                .errorPolicy(ErrorPolicy.collect(10))
                .build();

        try (ExcelImports.StagedFile staged = staged(document)) {
            ImportResult result = ExcelImports.execute(staged, plan);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.totalRows()).isEqualTo(2);
            assertThat(result.successRows()).isEqualTo(2);
            assertThat(result.sheets()).containsKeys("主表", "明细");
        }
        assertThat(mainRows).singleElement().satisfies(row -> {
            assertThat(row.code).isEqualTo("A-1");
            assertThat(row.quantity).isEqualTo(3);
            assertThat(row.date).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(row.enabled).isTrue();
            assertThat(row.amount).isEqualByComparingTo("12.50");
        });
        assertThat(detailRows).singleElement().satisfies(row -> {
            assertThat(row.parentCode).isEqualTo("A-1");
            assertThat(row.name).isEqualTo("试剂");
            assertThat(row.quantity).isEqualTo(2);
        });
    }

    @Test
    void shouldCollectRowErrorsAndProduceDownloadableErrorFile() throws Exception {
        ExcelDocument document = ExcelExports.write("records", MainRow.class, List.of(
                new MainRow("valid", 1, LocalDate.of(2027, 1, 1), true, BigDecimal.ONE),
                new MainRow("invalid", -1, LocalDate.of(2027, 1, 1), true, BigDecimal.ONE)),
                new ExcelExports.WriteOptions("主表", true));

        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("主表", MainRow.class)
                        .validator((row, rowNumber) -> {
                            if (row.quantity < 0) {
                                throw new RowValidationException(
                                        "EXCEL_NEGATIVE", "数量", "数量不能为负数");
                            }
                        })
                        .build())
                .errorPolicy(ErrorPolicy.collect(10))
                .build();

        ImportResult result;
        try (ExcelImports.StagedFile staged = staged(document)) {
            result = ExcelImports.execute(staged, plan);
        }
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.code()).isEqualTo("EXCEL_NEGATIVE");
            assertThat(error.columnName()).isEqualTo("数量");
            assertThat(error.rawCells().get(0)).isEqualTo("invalid");
        });
        assertThat(result.errorFile()).isPresent();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.errorFile().orElseThrow().writeTo(output);
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(output.toByteArray()))) {
            Sheet errors = workbook.getSheet("错误-主表");
            assertThat(errors).isNotNull();
            assertThat(errors.getRow(0).getCell(0).getStringCellValue()).isEqualTo("行号");
            assertThat(errors.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(3);
            assertThat(errors.getRow(1).getCell(2).getStringCellValue()).isEqualTo("invalid");
            assertThat(errors.getRow(1).getCell(7).getStringCellValue()).isEqualTo("数量");
            assertThat(errors.getRow(1).getCell(8).getStringCellValue())
                    .isEqualTo("数量不能为负数");
            assertThat(errors.getRow(1).getCell(9).getStringCellValue())
                    .isEqualTo("EXCEL_NEGATIVE");
        }
    }

    @Test
    void shouldRejectHeaderMismatchBeforeProcessingRows() {
        ExcelDocument document = ExcelExports.write("wrong", WrongRow.class,
                List.of(new WrongRow("A", "B")), new ExcelExports.WriteOptions("主表", true));
        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("主表", MainRow.class).build())
                .build();

        try (ExcelImports.StagedFile staged = staged(document)) {
            assertThatThrownBy(() -> ExcelImports.execute(staged, plan))
                    .isInstanceOf(HeaderMismatchException.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void shouldReportMissingRequiredCellsWithStableCode() {
        ExcelDocument document = ExcelExports.write("required", MainRow.class,
                List.of(new MainRow(null, 1, LocalDate.of(2027, 1, 1), true, BigDecimal.ONE)),
                new ExcelExports.WriteOptions("主表", true));
        ImportPlan plan = ImportPlan.builder()
                .sheet(SheetImportSpec.builder("主表", MainRow.class).build())
                .errorPolicy(ErrorPolicy.collect(10))
                .build();

        try (ExcelImports.StagedFile staged = staged(document)) {
            ImportResult result = ExcelImports.execute(staged, plan);
            assertThat(result.errors()).singleElement().satisfies(error -> {
                assertThat(error.code()).isEqualTo("EXCEL_REQUIRED");
                assertThat(error.rowNumber()).isEqualTo(2);
                assertThat(error.columnName()).isEqualTo("编号");
            });
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static ExcelImports.StagedFile staged(ExcelDocument document) {
        byte[] content = document.content();
        return ExcelImports.stage(document.filename(), new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length));
    }

    public static class MainRow {
        @ExcelColumn(name = "编号", index = 0, required = true)
        private String code;
        @ExcelColumn(name = "数量", index = 1, required = true)
        private Integer quantity;
        @ExcelColumn(name = "日期", index = 2, format = "yyyy-MM-dd")
        private LocalDate date;
        @ExcelColumn(name = "启用", index = 3)
        private Boolean enabled;
        @ExcelColumn(name = "金额", index = 4)
        private BigDecimal amount;

        public MainRow() {
        }

        MainRow(String code, Integer quantity, LocalDate date, Boolean enabled, BigDecimal amount) {
            this.code = code;
            this.quantity = quantity;
            this.date = date;
            this.enabled = enabled;
            this.amount = amount;
        }
    }

    public static class DetailRow {
        @ExcelColumn(name = "主表编号", index = 0)
        private String parentCode;
        @ExcelColumn(name = "名称", index = 1)
        private String name;
        @ExcelColumn(name = "数量", index = 2)
        private Integer quantity;

        public DetailRow() {
        }

        DetailRow(String parentCode, String name, Integer quantity) {
            this.parentCode = parentCode;
            this.name = name;
            this.quantity = quantity;
        }
    }

    public static class WrongRow {
        @ExcelColumn(name = "编号", index = 0)
        private String code;
        @ExcelColumn(name = "名称", index = 1)
        private String name;

        public WrongRow() {
        }

        WrongRow(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
