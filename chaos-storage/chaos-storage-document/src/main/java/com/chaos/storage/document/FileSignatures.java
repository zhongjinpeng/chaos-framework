package com.chaos.storage.document;

import com.chaos.core.io.ZipArchives;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Lightweight signature checks. These do not prove a file is safe or fully parseable.
 * In particular DOC and XLS share the same OLE signature; use OfficeDocuments for strict checks.
 */
public final class FileSignatures {
    private static final byte[] OLE = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };

    private FileSignatures() {}

    /**
     * Tests a normalized extension against file signatures and bounded ZIP entry names.
     * @param extension lowercase extension without a dot
     * @param content nonempty file bytes; null returns false
     * @param zipLimits caller-selected expansion limits for DOCX/XLSX
     * @return whether signatures match; malformed or oversized archives return false
     */
    public static boolean matches(String extension, byte[] content, ZipArchives.Limits zipLimits) {
        if (content == null || extension == null) return false;
        return switch (extension) {
            case "pdf" ->
                    starts(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))
                            && new String(
                                            content,
                                            Math.max(0, content.length - 1024),
                                            Math.min(1024, content.length),
                                            StandardCharsets.US_ASCII)
                                    .contains("%%EOF");
            case "png" ->
                    starts(
                            content,
                            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "jpg", "jpeg" ->
                    content.length >= 4
                            && (content[0] & 0xff) == 0xff
                            && (content[1] & 0xff) == 0xd8
                            && (content[2] & 0xff) == 0xff
                            && (content[content.length - 2] & 0xff) == 0xff
                            && (content[content.length - 1] & 0xff) == 0xd9;
            case "doc", "xls" -> starts(content, OLE);
            case "docx" -> openXml(content, "word/", zipLimits);
            case "xlsx" -> openXml(content, "xl/", zipLimits);
            default -> false;
        };
    }

    private static boolean starts(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
    }

    private static boolean openXml(byte[] content, String directory, ZipArchives.Limits limits) {
        if (!starts(content, new byte[] {0x50, 0x4b, 0x03, 0x04})) return false;
        try {
            var names = ZipArchives.inspect(content, limits);
            return names.contains("[Content_Types].xml")
                    && names.stream().anyMatch(name -> name.startsWith(directory));
        } catch (IOException ex) {
            return false;
        }
    }
}
