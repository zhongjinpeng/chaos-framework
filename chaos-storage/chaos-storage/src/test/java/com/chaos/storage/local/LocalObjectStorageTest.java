package com.chaos.storage.local;

import static org.junit.jupiter.api.Assertions.*;

import com.chaos.core.digest.Sha256;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

class LocalObjectStorageTest {
    @TempDir Path root;

    @Test
    void streamsBytesAndReturnsActualDigestWithoutClosingCallerStream() throws Exception {
        var storage = new LocalObjectStorage(root, Pattern.compile("tenant/.+"));
        byte[] bytes = {1, 2, 3};
        var input =
                new ByteArrayInputStream(bytes) {
                    @Override
                    public void close() {
                        fail("Caller owns stream");
                    }
                };
        var stored = storage.write("tenant/document.bin", input);
        assertEquals(3, stored.size());
        assertEquals(Sha256.hex(bytes), stored.sha256());
        try (var read = storage.open("tenant/document.bin")) {
            assertArrayEquals(bytes, read.readAllBytes());
        }
        storage.delete("tenant/document.bin");
        storage.delete("tenant/document.bin");
    }

    @Test
    void rejectsEscapesAndSymlinksWhileAllowingTrustedRootMount() throws Exception {
        var storage = new LocalObjectStorage(root.resolve("volume"), Pattern.compile(".+"));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.write("../../escape", new ByteArrayInputStream(new byte[0])));
        Files.createDirectories(root.resolve("volume"));
        Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("volume/link"), root.resolve("outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.open("link/file.bin"));
        Files.createSymbolicLink(root.resolve("mounted"), root.resolve("outside"));
        new LocalObjectStorage(root.resolve("mounted"), Pattern.compile(".+"))
                .write("test.bin", new ByteArrayInputStream(new byte[] {1}));
        assertTrue(Files.exists(root.resolve("outside/test.bin")));
    }

    @Test
    void objectStoragePortSeparatesBucketsAndNeverLeaksFileUrls() throws Exception {
        var client = new LocalObjectStorageClient(root);
        assertEquals(
                3,
                client.put(
                                "one",
                                "path/file.bin",
                                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                                -1,
                                "application/octet-stream")
                        .size());
        try (var stream = client.get("one", "path/file.bin")) {
            assertEquals(3, stream.readAllBytes().length);
        }
        assertThrows(IllegalArgumentException.class, () -> client.get("../one", "file.bin"));
        assertThrows(IllegalArgumentException.class, () -> client.get("one", "../file.bin"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> client.presignedGetUrl("one", "path/file.bin", Duration.ofMinutes(1)));
        client.delete("one", "path/file.bin");
    }
}
