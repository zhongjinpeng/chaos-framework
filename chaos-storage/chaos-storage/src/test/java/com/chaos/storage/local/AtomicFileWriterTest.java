package com.chaos.storage.local;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class AtomicFileWriterTest {
    @TempDir Path root;

    @Test
    void publishesOnlyAfterCompleteCopy() throws IOException {
        Path target = root.resolve("tenant/document.bin");
        InputStream source =
                new ByteArrayInputStream(new byte[] {1, 2, 3}) {
                    @Override
                    public int read(byte[] bytes, int offset, int length) {
                        assertFalse(Files.exists(target));
                        return super.read(bytes, offset, length);
                    }

                    @Override
                    public long transferTo(java.io.OutputStream output) throws IOException {
                        assertFalse(Files.exists(target));
                        return super.transferTo(output);
                    }
                };
        AtomicFileWriter.write(target, source);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(target));
    }

    @Test
    void failedCopyLeavesNoPartialOrTemporaryFile() throws IOException {
        Path target = root.resolve("upload.bin");
        var source =
                new InputStream() {
                    private boolean first = true;

                    @Override
                    public int read() throws IOException {
                        if (!first) throw new IOException("broken upload");
                        first = false;
                        return 42;
                    }
                };
        assertEquals(
                "broken upload",
                assertThrows(IOException.class, () -> AtomicFileWriter.write(target, source))
                        .getMessage());
        try (var remaining = Files.list(root)) {
            assertEquals(0, remaining.count());
        }
    }
}
