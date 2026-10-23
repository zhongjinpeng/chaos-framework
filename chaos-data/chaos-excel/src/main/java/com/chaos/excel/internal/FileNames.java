package com.chaos.excel.internal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * 下载文件名与 Sheet 名规范化。
 */
public final class FileNames {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final int MAX_FILENAME_LENGTH = 200;

    private FileNames() {
    }

    /**
     * 规范化 XLSX 文件名：去除路径、控制字符并补齐后缀。
     *
     * @param filename 原始文件名
     * @return 规范化后的文件名
     */
    public static String xlsx(String filename) {
        String safe = baseName(filename);
        return safe.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? safe : safe + ".xlsx";
    }

    /**
     * 去除扩展名和路径，保留安全的文件基础名。
     *
     * @param filename 原始文件名
     * @return 安全的基础名
     */
    public static String baseName(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        String normalized = filename.strip().replace('\\', '/');
        String safe = normalized.substring(normalized.lastIndexOf('/') + 1);
        safe = safe.replaceAll("[\\p{Cntrl}]", "").strip();
        if (safe.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            safe = safe.substring(0, safe.length() - 5);
        }
        if (safe.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        return safe.length() > MAX_FILENAME_LENGTH
                ? safe.substring(0, MAX_FILENAME_LENGTH) : safe;
    }

    /**
     * 生成带时间戳的错误文件名。
     *
     * @param base 基础文件名
     * @param marker 文件名标记
     * @return 带时间戳的 XLSX 文件名
     */
    public static String timestamped(String base, String marker) {
        return xlsx(baseName(base) + marker + "-" + LocalDateTime.now().format(TIMESTAMP));
    }

    /**
     * 生成合法且不冲突的 Sheet 名。
     *
     * @param desired 期望名称
     * @param existing 已存在的 Sheet 名
     * @return 合法且唯一的 Sheet 名
     */
    public static String sheetName(String desired, Set<String> existing) {
        String base = sanitizeSheetName(desired);
        if (!existing.contains(base)) {
            return base;
        }
        for (int index = 2; index < 1000; index++) {
            String suffix = "_" + index;
            String prefix = base.length() + suffix.length() > MAX_SHEET_NAME_LENGTH
                    ? base.substring(0, MAX_SHEET_NAME_LENGTH - suffix.length()) : base;
            String candidate = prefix + suffix;
            if (!existing.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Cannot create a unique sheet name for: " + desired);
    }

    private static String sanitizeSheetName(String desired) {
        if (desired == null || desired.isBlank()) {
            return "Sheet";
        }
        String safe = desired.strip().replaceAll("[\\\\/*?:\\[\\]]", "_");
        if (safe.isBlank()) {
            safe = "Sheet";
        }
        return safe.length() > MAX_SHEET_NAME_LENGTH
                ? safe.substring(0, MAX_SHEET_NAME_LENGTH) : safe;
    }
}
