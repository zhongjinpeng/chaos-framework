package com.chaos.core.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ZipArchivesTest {
    @TempDir Path root;

    @Test
    void checksExpandedSizeEntryCountAndCompressionRatio() throws Exception {
        byte[] archive = archive();
        assertThrows(
                IOException.class,
                () -> ZipArchives.inspect(archive, new ZipArchives.Limits(10, 10, 1000)));
        assertThrows(
                IOException.class,
                () -> ZipArchives.inspect(archive, new ZipArchives.Limits(100000, 1, 1000)));
        assertThrows(
                IOException.class,
                () -> ZipArchives.inspect(archive, new ZipArchives.Limits(100000, 10, 2)));
        var limits = new ZipArchives.Limits(100000, 10, 1000);
        assertEquals(2, ZipArchives.inspect(archive, limits).size());
        Path staged = root.resolve("source.zip");
        Files.write(staged, archive);
        assertEquals(ZipArchives.inspect(archive, limits), ZipArchives.inspect(staged, limits));
        assertTrue(Files.exists(staged));
        assertThrows(IOException.class, () -> ZipArchives.inspect(new byte[] {1, 2}, limits));
    }

    private byte[] archive() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (String name : new String[] {"one", "two"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[10000]);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
