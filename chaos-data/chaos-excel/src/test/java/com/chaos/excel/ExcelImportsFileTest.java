package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ExcelImportsFileTest {

    @Test
    void shouldStageStreamWithSafeNameSizeAndDigest() throws Exception {
        byte[] content = "streamed workbook".getBytes(StandardCharsets.UTF_8);

        try (ExcelImports.StagedFile file = ExcelImports.stage(
                "C:\\fakepath\\records.XLSX",
                new ByteArrayInputStream(content),
                ExcelImports.Options.xlsx(content.length))) {
            assertThat(file.filename()).isEqualTo("records.XLSX");
            assertThat(file.size()).isEqualTo(content.length);
            assertThat(file.sha256()).isEqualTo(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)));
            try (var input = file.openStream()) {
                assertThat(input.readAllBytes()).isEqualTo(content);
            }
        }
    }

    @Test
    void shouldRejectInvalidUploadMetadataAndContent() {
        ExcelImports.Options options = ExcelImports.Options.xlsx(3);

        assertThatThrownBy(() -> ExcelImports.stage(
                null, new ByteArrayInputStream(new byte[] {1}), options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("filename");
        assertThatThrownBy(() -> ExcelImports.stage(
                "records.xls", new ByteArrayInputStream(new byte[] {1}), options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("extension");
        assertThatThrownBy(() -> ExcelImports.stage(
                "records.xlsx", new ByteArrayInputStream(new byte[0]), options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> ExcelImports.stage(
                "records.xlsx", new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("exceeds limit");
    }

    @Test
    void shouldRejectOpeningFileAfterClose() throws Exception {
        ExcelImports.StagedFile file = ExcelImports.stage(
                "records.xlsx",
                new ByteArrayInputStream(new byte[] {1}),
                ExcelImports.Options.xlsx(1));

        file.close();

        assertThatThrownBy(file::openStream)
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldKeepTheUploadStreamOwnedByTheCaller() throws Exception {
        TrackingInputStream input = new TrackingInputStream(new byte[] {1});

        try (ExcelImports.StagedFile ignored = ExcelImports.stage(
                "records.xlsx", input, ExcelImports.Options.xlsx(1))) {
            assertThat(input.closed).isFalse();
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
