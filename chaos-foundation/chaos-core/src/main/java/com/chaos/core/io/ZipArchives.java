package com.chaos.core.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Bounded ZIP inspection shared by file detection and spreadsheet import.
 * Entry content is streamed and discarded; names are returned only after the entire scan.
 */
public final class ZipArchives {
    private ZipArchives() {}

    /**
     * Per-archive resource limits; no JVM-global ZIP settings are modified.
     * @param maxUncompressedBytes maximum aggregate expanded bytes
     * @param maxEntries maximum entries, including directories and duplicate names
     * @param maxCompressionRatio maximum aggregate expanded/compressed ratio
     */
    public record Limits(long maxUncompressedBytes, int maxEntries, int maxCompressionRatio) {
        public Limits {
            if (maxUncompressedBytes < 1 || maxEntries < 1 || maxCompressionRatio < 1) {
                throw new IllegalArgumentException("ZIP limits must be positive");
            }
        }
    }

    /**
     * Scans an in-memory archive through a temporary file, removed on success and failure.
     * Random access preserves ZIP64/data-descriptor compatibility and validates the central directory.
     * @param content compressed bytes already subject to the caller's upload limit
     * @param limits expansion and entry limits
     * @return immutable set of entry names
     * @throws IOException when ZIP data is corrupt or exceeds limits
     */
    public static Set<String> inspect(byte[] content, Limits limits) throws IOException {
        Path staged = Files.createTempFile("chaos-archive-", ".zip");
        Set<String> names;
        try {
            Files.write(staged, content);
            names = inspect(staged, limits);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException | SecurityException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        Files.deleteIfExists(staged);
        return names;
    }

    /**
     * Scans a staged archive, closing the internally opened file stream.
     * @param path caller-owned staged file, never deleted by this method
     * @param limits expansion and entry limits
     * @return immutable set of entry names
     * @throws IOException when the file is unreadable, corrupt or exceeds limits
     */
    public static Set<String> inspect(Path path, Limits limits) throws IOException {
        // ZipFile also validates the central directory of staged files before parsing.
        try (var zip = new ZipFile(path.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
            if (zip.size() > limits.maxEntries()) {
                throw new IOException("ZIP archive contains too many entries");
            }
            Set<String> names = new HashSet<>();
            byte[] buffer = new byte[16 * 1024];
            long expanded = 0;
            long compressed = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                names.add(entry.getName());
                try (var input = zip.getInputStream(entry)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (read > limits.maxUncompressedBytes() - expanded) {
                            throw new IOException("ZIP archive expands beyond limit");
                        }
                        expanded += read;
                    }
                }
                compressed += Math.max(0, entry.getCompressedSize());
            }
            if (compressed > 0 && (double) expanded / compressed > limits.maxCompressionRatio()) {
                throw new IOException("ZIP compression ratio exceeds limit");
            }
            return Set.copyOf(names);
        }
    }
}
