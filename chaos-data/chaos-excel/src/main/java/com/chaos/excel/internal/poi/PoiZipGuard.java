package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * XLSX 压缩包预检，防止压缩炸弹在 POI 解析前耗尽内存。
 */
public final class PoiZipGuard {

    private static final int BUFFER_SIZE = 16 * 1024;

    private PoiZipGuard() {
    }

    /**
     * 检查压缩包条目数、解压后总大小和压缩比。
     *
     * @param path XLSX 文件
     * @param maxUncompressedBytes 解压后允许的最大字节数
     * @param maxEntries 允许的最大条目数
     * @param maxCompressionRatio 允许的最大压缩比
     * @throws ExcelProcessingException 压缩包不合法或超过限制时抛出
     */
    public static void check(
            Path path, long maxUncompressedBytes, int maxEntries, int maxCompressionRatio) {
        try (ZipFile zipFile = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            if (zipFile.size() > maxEntries) {
                throw new ExcelProcessingException(
                        "Excel archive contains too many entries: " + zipFile.size());
            }
            long total = 0;
            long compressedTotal = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[BUFFER_SIZE];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getSize() > 0 && total + entry.getSize() > maxUncompressedBytes) {
                    throw new ExcelProcessingException(
                            "Excel archive expands beyond limit: " + maxUncompressedBytes);
                }
                try (InputStream input = zipFile.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > maxUncompressedBytes) {
                            throw new ExcelProcessingException(
                                    "Excel archive expands beyond limit: " + maxUncompressedBytes);
                        }
                    }
                }
                if (entry.getCompressedSize() > 0) {
                    compressedTotal += entry.getCompressedSize();
                }
            }
            if (compressedTotal > 0 && total / compressedTotal > maxCompressionRatio) {
                throw new ExcelProcessingException(
                        "Excel archive compression ratio is suspicious: " + total / compressedTotal);
            }
        } catch (IOException exception) {
            throw new ExcelProcessingException("Excel archive cannot be inspected", exception);
        }
    }
}
