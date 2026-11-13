package com.chaos.storage.document;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 校验上传后缀与 Office 容器内容一致，拒绝加密和宏内容。
 */
public final class OfficeDocuments {
    private OfficeDocuments() {}

    /** Supported Office formats, independent of application enums. */
    public enum Format {
        XLS,
        XLSX,
        DOC,
        DOCX
    }

    /** Stable technical rejection reasons for caller-owned business error mapping. */
    public enum Reason {
        INVALID,
        MISMATCH,
        ENCRYPTED,
        MACROS
    }

    /** Typed container rejection; contains no business error code. */
    public static final class Rejected extends IllegalArgumentException {
        private final Reason reason;

        private Rejected(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        /** @return technical rejection reason */
        public Reason reason() {
            return reason;
        }
    }

    /**
     * 校验上传文件与声明的文档格式一致。
     *
     * @param content 原始文件内容
     * @param limits ZIP 解压资源上限，不修改 POI 全局配置
     * @param format 声明的 Office 文档格式
     */
    public static void validate(
            byte[] content, Format format, com.chaos.core.io.ZipArchives.Limits limits) {
        java.util.Objects.requireNonNull(format, "format");
        java.util.Objects.requireNonNull(limits, "limits");
        if (content == null || content.length < 8) {
            invalid(Reason.INVALID, "文件为空或不是有效的 Office 文档");
        }

        boolean legacy = format == Format.XLS || format == Format.DOC;

        try {
            if (legacy) {
                if (FileMagic.valueOf(content) != FileMagic.OLE2) {
                    invalid(Reason.MISMATCH, "文件内容与后缀不匹配，请使用原格式文件");
                }

                try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(content))) {
                    DirectoryNode root = fs.getRoot();
                    if (root.hasEntry("EncryptedPackage") || root.hasEntry("EncryptionInfo")) {
                        invalid(Reason.ENCRYPTED, "不支持加密文档，请解除密码后上传");
                    }

                    if (hasMacros(root)) {
                        invalid(Reason.MACROS, "不支持包含宏的文档，请移除宏后上传");
                    }

                    if (format == Format.DOC && !root.hasEntry("WordDocument")
                            || format == Format.XLS
                                    && !root.hasEntry("Workbook")
                                    && !root.hasEntry("Book")) {
                        invalid(Reason.MISMATCH, "文件内容与 Excel / Word 后缀不匹配");
                    }
                }
            } else {
                if (FileMagic.valueOf(content) != FileMagic.OOXML) {
                    invalid(Reason.MISMATCH, "文件内容与后缀不匹配或已加密");
                }

                com.chaos.core.io.ZipArchives.inspect(content, limits);
                try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
                    String expected =
                            format == Format.XLSX
                                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
                                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
                    boolean found = false;

                    for (PackagePart part : pkg.getParts()) {
                        String type = part.getContentType().toLowerCase(Locale.ROOT);
                        if (type.contains("macroenabled") || type.contains("vbaproject")) {
                            invalid(Reason.MACROS, "不支持包含宏的文档");
                        }

                        if (part.getContentType().equals(expected)) {
                            found = true;
                        }
                    }

                    if (!found) {
                        invalid(Reason.MISMATCH, "文件内容与 Excel / Word 后缀不匹配");
                    }
                }
            }
        } catch (Rejected ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Rejected(Reason.INVALID, "文档已损坏、加密或格式不受支持", ex);
        }
    }

    private static boolean hasMacros(DirectoryEntry dir) throws IOException {
        for (Entry entry : dir) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (name.equals("vba") || name.equals("macros") || name.contains("_vba_project")) {
                return true;
            }

            if (entry instanceof DirectoryEntry child) {
                if (hasMacros(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void invalid(Reason reason, String message) {
        throw new Rejected(reason, message, null);
    }
}
