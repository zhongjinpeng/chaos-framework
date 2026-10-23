package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.excel.annotation.ExcelColumn;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelFacadesTest {

    @Test
    void shouldWriteToCallerStreamAndReadStagedFileWithoutClosingOutput() throws Exception {
        TrackingOutputStream output = new TrackingOutputStream();
        List<TestRow> expected = List.of(new TestRow("A-001", "恒温室", 23),
                new TestRow("A-002", "养护室", 25));

        ExcelExports.write(output, TestRow.class, expected, new ExcelExports.WriteOptions("环境记录", true));

        assertThat(output.closed).isFalse();
        try (ExcelImports.StagedFile staged = staged(output.toByteArray())) {
            List<TestRow> actual = ExcelImports.read(staged, TestRow.class);
            assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Test
    void shouldReadInConfiguredBatches() throws Exception {
        ExcelDocument document = ExcelExports.write("records", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2),
                        new TestRow("C", "三室", 3)));
        List<List<TestRow>> batches = new ArrayList<>();

        try (ExcelImports.StagedFile staged = staged(document.content())) {
            ExcelImports.readBatches(staged, TestRow.class,
                    new ExcelImports.ReadOptions(0, 1, 2, 10), batches::add);

            assertThat(batches).hasSize(2);
            assertThat(batches.get(0)).hasSize(2);
            assertThat(batches.get(1)).hasSize(1);
            assertThat(document.filename()).isEqualTo("records.xlsx");
            assertThat(document.contentType()).isEqualTo(ExcelDocument.XLSX_CONTENT_TYPE);
        }
    }

    @Test
    void shouldWriteBatchesWithoutClosingCallerStreamOrRepeatingTheHeader() throws Exception {
        TrackingOutputStream output = new TrackingOutputStream();
        List<List<TestRow>> batches = List.of(
                List.of(new TestRow("A", "一室", 1)),
                List.of(new TestRow("B", "二室", 2)));

        ExcelExports.writeBatches(output, TestRow.class, batches,
                new ExcelExports.WriteOptions("环境记录", true));

        assertThat(output.closed).isFalse();
        try (ExcelImports.StagedFile staged = staged(output.toByteArray())) {
            assertThat(ExcelImports.read(staged, TestRow.class))
                    .extracting(TestRow::getCode)
                    .containsExactly("A", "B");
        }
    }

    @Test
    void shouldCreateDeferredExportAndWriteItWithoutClosingTheDestination() throws Exception {
        boolean[] sourceInvoked = {false};
        StreamingExcelFile export = ExcelExports.exportBatches(
                "C:\\exports\\records",
                TestRow.class,
                () -> {
                    sourceInvoked[0] = true;
                    return List.of(List.of(new TestRow("A", "一室", 1)));
                },
                new ExcelExports.WriteOptions("环境记录", true));
        TrackingOutputStream output = new TrackingOutputStream();

        assertThat(sourceInvoked[0]).isFalse();
        assertThat(export.filename()).isEqualTo("records.xlsx");
        assertThat(export.contentType()).isEqualTo(ExcelDocument.XLSX_CONTENT_TYPE);
        export.writeTo(output);

        assertThat(sourceInvoked[0]).isTrue();
        assertThat(output.closed).isFalse();
        try (ExcelImports.StagedFile staged = staged(output.toByteArray())) {
            assertThat(ExcelImports.read(staged, TestRow.class))
                    .extracting(TestRow::getCode)
                    .containsExactly("A");
        }
    }

    @Test
    void shouldRejectFilesExceedingTheRowLimit() throws Exception {
        ExcelDocument document = ExcelExports.write("records.xlsx", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2),
                        new TestRow("C", "三室", 3)));

        try (ExcelImports.StagedFile staged = staged(document.content())) {
            assertThatThrownBy(() -> ExcelImports.read(staged,
                    TestRow.class, new ExcelImports.ReadOptions(0, 1, 10, 2)))
                    .isInstanceOfSatisfying(RowLimitExceededException.class,
                            exception -> assertThat(exception.maxRows()).isEqualTo(2));
        }
    }

    @Test
    void shouldReadStagedFileWithConfiguredLimits() throws Exception {
        ExcelDocument document = ExcelExports.write("records.xlsx", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2)));

        try (ExcelImports.StagedFile staged = staged(document.content())) {
            List<TestRow> rows = ExcelImports.read(staged, TestRow.class,
                    new ExcelImports.ReadOptions(0, 1, 1, 2));
            assertThat(rows).hasSize(2);
        }
    }

    @Test
    void shouldReadRowsFromDiskStagedUpload() throws Exception {
        ExcelDocument document = ExcelExports.write("records.xlsx", TestRow.class,
                List.of(new TestRow("A", "一室", 1), new TestRow("B", "二室", 2)));

        try (ExcelImports.StagedFile staged = ExcelImports.stage(
                document.filename(),
                new ByteArrayInputStream(document.content()),
                ExcelImports.Options.xlsx(document.content().length))) {
            ExcelImports.StagedData<TestRow> data = ExcelImports.readRequired(
                    staged, TestRow.class, ExcelImports.ReadOptions.defaults());

            assertThat(data.file().sha256()).hasSize(64);
            assertThat(data.rows()).hasSize(2);
        }
    }

    private static ExcelImports.StagedFile staged(byte[] content) {
        return ExcelImports.stage("records.xlsx", new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length));
    }

    public static class TestRow {
        @ExcelColumn(name = "设备编号", index = 0)
        private String code;
        @ExcelColumn(name = "监测位置", index = 1)
        private String location;
        @ExcelColumn(name = "温度", index = 2)
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
