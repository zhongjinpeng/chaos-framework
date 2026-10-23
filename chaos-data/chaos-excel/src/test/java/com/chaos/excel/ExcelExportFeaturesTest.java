package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.excel.annotation.ExcelColumn;
import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelExportFeaturesTest {

    @Test
    void shouldExportMultipleSheetsAndSanitizeFormulaLikeText() throws Exception {
        ExcelDocument document = ExcelExports.write("export", List.of(
                ExportSheet.of("A", ExportRow.class, List.of(List.of(
                        new ExportRow("=SUM(A1)", 1)))),
                ExportSheet.of("B", ExportRow.class, List.of(List.of(
                        new ExportRow("normal", 2))))),
                ExportOptions.defaults().autoFilter(true));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(document.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet first = workbook.getSheet("A");
            assertThat(first.getRow(0).getCell(0).getStringCellValue()).isEqualTo("文本");
            assertThat(first.getRow(1).getCell(0).getStringCellValue()).isEqualTo("'=SUM(A1)");
            assertThat(first.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(workbook.getSheet("B").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("normal");
        }
    }

    @Test
    void shouldNotCloseCallerOutputStreamWhenExportingBatches() {
        TrackingOutputStream output = new TrackingOutputStream();
        ExcelExports.writeBatches(output, ExportRow.class,
                List.of(List.of(new ExportRow("A", 1)), List.of(new ExportRow("B", 2))),
                new ExcelExports.WriteOptions("数据", true));

        assertThat(output.closed).isFalse();
        assertThat(output.size()).isGreaterThan(0);
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    public static class ExportRow {
        @ExcelColumn(name = "文本", index = 0)
        private String text;
        @ExcelColumn(name = "数量", index = 1)
        private Integer quantity;

        public ExportRow() {
        }

        ExportRow(String text, Integer quantity) {
            this.text = text;
            this.quantity = quantity;
        }
    }
}
