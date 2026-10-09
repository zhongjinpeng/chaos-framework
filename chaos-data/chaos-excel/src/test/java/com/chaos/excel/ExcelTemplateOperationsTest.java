package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;

import cn.idev.excel.annotation.ExcelProperty;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTemplateOperationsTest {

    @Test
    void shouldCreateStyledMultiSheetTemplateWithNamedDropdowns() throws Exception {
        ExcelTemplateSupport.SheetDefinition definition = new ExcelTemplateSupport.SheetDefinition(
                "试剂", new int[]{20, 24}, Map.of(
                        1, new ExcelTemplateSupport.Dropdown("units", List.of("g", "mL"))));

        ExcelDocument document = ExcelTemplateOperations.write("试剂导入模板",
                List.of(new ExcelTemplateOperations.Sheet("试剂", TemplateRow.class,
                        List.of(new TemplateRow("氯化钠", "g")), definition)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(document.content()))) {
            assertThat(document.filename()).isEqualTo("试剂导入模板.xlsx");
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex(ExcelTemplateSupport.DICTIONARY_SHEET)))
                    .isTrue();

            Sheet sheet = workbook.getSheet("试剂");
            assertThat(sheet.getColumnWidth(0)).isEqualTo(20 * 256);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            var header = sheet.getRow(0).getCell(0);
            var headerFont = workbook.getFontAt(header.getCellStyle().getFontIndex());
            assertThat(headerFont.getFontName()).isEqualTo("宋体");
            assertThat(headerFont.getFontHeightInPoints()).isEqualTo((short) 12);
            assertThat(headerFont.getBold()).isTrue();
            assertThat(header.getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(header.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.GREY_25_PERCENT.getIndex());

            assertThat(sheet.getDataValidations()).singleElement().satisfies(validation -> {
                assertThat(validation.getValidationConstraint().getFormula1()).isEqualTo("units");
                assertThat(validation.getRegions().getCellRangeAddresses()).singleElement().satisfies(range -> {
                    assertThat(range.getFirstRow()).isEqualTo(1);
                    assertThat(range.getLastRow()).isEqualTo(4999);
                    assertThat(range.getFirstColumn()).isEqualTo(1);
                    assertThat(range.getLastColumn()).isEqualTo(1);
                });
            });

            Name units = workbook.getName("units");
            assertThat(units.getRefersToFormula()).isEqualTo("'_字典'!$A$1:$A$2");
            assertThat(workbook.getSheet(ExcelTemplateSupport.DICTIONARY_SHEET)
                    .getRow(1).getCell(0).getStringCellValue()).isEqualTo("mL");
        }

        assertThat(ExcelOperations.readVisibleSheets(document.content(), TemplateRow.class,
                new ExcelReadOptions(0, 1, 100, 100)))
                .extracting(TemplateRow::getName)
                .containsExactly("氯化钠");
    }

    public static class TemplateRow {
        @ExcelProperty(value = "名称", index = 0)
        private String name;

        @ExcelProperty(value = "单位", index = 1)
        private String unit;

        public TemplateRow() {
        }

        TemplateRow(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }
}
