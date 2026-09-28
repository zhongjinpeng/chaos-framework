package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.idev.excel.annotation.ExcelProperty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelOperationsTest {

    @Test
    void shouldWriteAndReadXlsxWithoutClosingCallerStream() {
        TrackingOutputStream output = new TrackingOutputStream();
        List<TestRow> expected = List.of(new TestRow("A-001", "恒温室", 23),
                new TestRow("A-002", "养护室", 25));

        ExcelOperations.write(output, TestRow.class, expected, new ExcelWriteOptions("环境记录", true));

        assertThat(output.closed).isFalse();
        List<TestRow> actual = ExcelOperations.read(new ByteArrayInputStream(output.toByteArray()),
                TestRow.class, ExcelReadOptions.defaults());
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReadInConfiguredBatches() {
        ExcelDocument document = ExcelOperations.write("records", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2),
                        new TestRow("C", "三室", 3)));
        List<List<TestRow>> batches = new ArrayList<>();

        ExcelOperations.readBatches(new ByteArrayInputStream(document.content()), TestRow.class,
                new ExcelReadOptions(0, 1, 2, 10), batches::add);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(1);
        assertThat(document.filename()).isEqualTo("records.xlsx");
        assertThat(document.contentType()).isEqualTo(ExcelDocument.XLSX_CONTENT_TYPE);
    }

    @Test
    void shouldRejectFilesExceedingTheRowLimit() {
        ExcelDocument document = ExcelOperations.write("records.xlsx", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2),
                        new TestRow("C", "三室", 3)));

        assertThatThrownBy(() -> ExcelOperations.read(new ByteArrayInputStream(document.content()),
                TestRow.class, new ExcelReadOptions(0, 1, 10, 2)))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("row limit exceeded");
    }

    public static class TestRow {
        @ExcelProperty(value = "设备编号", index = 0)
        private String code;
        @ExcelProperty(value = "监测位置", index = 1)
        private String location;
        @ExcelProperty(value = "温度", index = 2)
        private Integer temperature;

        public TestRow() {
        }

        TestRow(String code, String location, Integer temperature) {
            this.code = code;
            this.location = location;
            this.temperature = temperature;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Integer getTemperature() { return temperature; }
        public void setTemperature(Integer temperature) { this.temperature = temperature; }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
