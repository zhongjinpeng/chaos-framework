package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ExcelImportsFileTest {

    @Test
    void shouldPrepareSafeXlsxFileAndDigest() throws Exception {
        byte[] content = "workbook".getBytes(StandardCharsets.UTF_8);

        ExcelImports.File file = ExcelImports.prepare(
                "C:\\fakepath\\records.XLSX", content, ExcelImports.Options.xlsx(1024));

        assertThat(file.filename()).isEqualTo("records.XLSX");
        assertThat(file.content()).isEqualTo(content);
        assertThat(file.sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)));
    }

    @Test
    void shouldDefensivelyCopyPreparedContent() {
        byte[] content = {1, 2, 3};
        ExcelImports.File file =
                ExcelImports.prepare("records.xlsx", content, ExcelImports.Options.xlsx(3));

        content[0] = 9;
        byte[] returned = file.content();
        returned[1] = 9;

        assertThat(file.content()).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRejectInvalidUploadMetadataAndContent() {
        ExcelImports.Options options = ExcelImports.Options.xlsx(3);

        assertThatThrownBy(() -> ExcelImports.prepare(null, new byte[] {1}, options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("filename");
        assertThatThrownBy(() -> ExcelImports.prepare("records.xls", new byte[] {1}, options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("extension");
        assertThatThrownBy(() -> ExcelImports.prepare("records.xlsx", new byte[0], options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> ExcelImports.prepare("records.xlsx", new byte[4], options))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("exceeds limit");
    }
}
