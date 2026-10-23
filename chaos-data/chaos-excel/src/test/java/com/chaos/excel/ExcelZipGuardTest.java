package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chaos.excel.internal.poi.PoiZipGuard;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelZipGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectArchiveThatExpandsBeyondLimit() throws Exception {
        Path archive = tempDir.resolve("oversized.xlsx");
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(new byte[4096]);
            zip.closeEntry();
        }

        assertThatThrownBy(() -> PoiZipGuard.check(archive, 1024, 100, 1_000))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("expands beyond limit");
    }

    @Test
    void shouldRejectArchiveWithTooManyEntries() throws Exception {
        Path archive = tempDir.resolve("entries.xlsx");
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < 3; index++) {
                zip.putNextEntry(new ZipEntry("part-" + index + ".xml"));
                zip.write(new byte[] {1});
                zip.closeEntry();
            }
        }

        assertThatThrownBy(() -> PoiZipGuard.check(archive, 10_000, 2, 1_000))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("too many entries");
    }
}
