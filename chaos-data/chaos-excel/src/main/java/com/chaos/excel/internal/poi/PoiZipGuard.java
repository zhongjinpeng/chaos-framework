package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * XLSX 压缩包预检，防止压缩炸弹在 POI 解析前耗尽内存。
 */
public final class PoiZipGuard {

    private PoiZipGuard() {}

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
        try {
            com.chaos.core.io.ZipArchives.inspect(
                    path,
                    new com.chaos.core.io.ZipArchives.Limits(
                            maxUncompressedBytes, maxEntries, maxCompressionRatio));
        } catch (IOException exception) {
            throw new ExcelProcessingException(
                    "Excel archive cannot be inspected: " + exception.getMessage(), exception);
        }
    }
}
