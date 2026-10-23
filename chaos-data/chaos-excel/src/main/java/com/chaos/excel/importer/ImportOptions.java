package com.chaos.excel.importer;

/**
 * 导入边界与默认策略。
 *
 * @param maxBytes 上传文件最大字节数
 * @param maxRows 单 Sheet 最大数据行数
 * @param maxColumns 单 Sheet 最大列数
 * @param maxSheets 最大 Sheet 数
 * @param maxErrors 最多保留的错误数
 * @param maxUncompressedBytes 解压后最大字节数
 * @param maxZipEntries 压缩包最大条目数
 * @param maxCompressionRatio 最大压缩比
 * @param headerPolicy 表头校验策略
 */
public record ImportOptions(
        int maxBytes,
        int maxRows,
        int maxColumns,
        int maxSheets,
        int maxErrors,
        long maxUncompressedBytes,
        int maxZipEntries,
        int maxCompressionRatio,
        HeaderPolicy headerPolicy) {

    public ImportOptions {
        if (maxBytes < 1 || maxRows < 1 || maxColumns < 1 || maxSheets < 1
                || maxErrors < 1
                || maxUncompressedBytes < 1 || maxZipEntries < 1
                || maxCompressionRatio < 1) {
            throw new IllegalArgumentException("Import limits must be positive");
        }
        if (headerPolicy == null) {
            throw new IllegalArgumentException("headerPolicy must not be null");
        }
    }

    /** @return 20 MB、10 万行、256 列、20 Sheet 的默认配置 */
    public static ImportOptions defaults() {
        return new ImportOptions(
                20 * 1024 * 1024,
                100_000,
                256,
                20,
                1_000,
                200L * 1024 * 1024,
                10_000,
                200,
                HeaderPolicy.STRICT);
    }

    /**
     * 创建只允许 XLSX 且指定字节上限的配置。
     *
     * @param maxBytes 最大字节数
     * @return 导入配置
     */
    public static ImportOptions xlsx(int maxBytes) {
        ImportOptions defaults = defaults();
        return new ImportOptions(
                maxBytes,
                defaults.maxRows(),
                defaults.maxColumns(),
                defaults.maxSheets(),
                defaults.maxErrors(),
                defaults.maxUncompressedBytes(),
                defaults.maxZipEntries(),
                defaults.maxCompressionRatio(),
                defaults.headerPolicy());
    }
}
