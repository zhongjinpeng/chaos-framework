package com.chaos.storage.document;

import static org.junit.jupiter.api.Assertions.*;

import com.chaos.core.io.ZipArchives;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

class OfficeDocumentsTest {
    private static final ZipArchives.Limits LIMITS =
            new ZipArchives.Limits(256L * 1024 * 1024, 10000, 1000);

    @Test
    void acceptsWordAndExcelAndRejectsMismatchedContainers() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var word = new XWPFDocument()) {
            word.createParagraph().createRun().setText("Synthetic document");
            word.write(output);
        }
        byte[] docx = output.toByteArray();
        OfficeDocuments.validate(docx, OfficeDocuments.Format.DOCX, LIMITS);
        assertTrue(FileSignatures.matches("docx", docx, LIMITS));
        assertFalse(FileSignatures.matches("xlsx", docx, LIMITS));
        assertThrows(
                OfficeDocuments.Rejected.class,
                () -> OfficeDocuments.validate(docx, OfficeDocuments.Format.XLSX, LIMITS));
        assertThrows(
                OfficeDocuments.Rejected.class,
                () ->
                        OfficeDocuments.validate(
                                docx,
                                OfficeDocuments.Format.DOCX,
                                new ZipArchives.Limits(1, 100, 1000)));
        output.reset();
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Synthetic");
            workbook.write(output);
        }
        OfficeDocuments.validate(output.toByteArray(), OfficeDocuments.Format.XLSX, LIMITS);
        output.reset();
        try (var workbook = new HSSFWorkbook()) {
            workbook.createSheet("Synthetic");
            workbook.write(output);
        }
        OfficeDocuments.validate(output.toByteArray(), OfficeDocuments.Format.XLS, LIMITS);
    }

    @Test
    void strictInspectionRejectsMacrosAndEncryptionWithoutChangingSignaturePolicy()
            throws Exception {
        for (String entry : new String[] {"EncryptedPackage", "VBA"}) {
            var output = new ByteArrayOutputStream();
            try (var fs = new POIFSFileSystem()) {
                fs.getRoot()
                        .createDocument("WordDocument", new ByteArrayInputStream(new byte[] {1}));
                if (entry.equals("VBA")) fs.getRoot().createDirectory(entry);
                else fs.getRoot().createDocument(entry, new ByteArrayInputStream(new byte[] {1}));
                fs.writeFilesystem(output);
            }
            byte[] bytes = output.toByteArray();
            assertTrue(FileSignatures.matches("doc", bytes, LIMITS));
            var rejection =
                    assertThrows(
                            OfficeDocuments.Rejected.class,
                            () ->
                                    OfficeDocuments.validate(
                                            bytes, OfficeDocuments.Format.DOC, LIMITS));
            assertEquals(
                    entry.equals("VBA")
                            ? OfficeDocuments.Reason.MACROS
                            : OfficeDocuments.Reason.ENCRYPTED,
                    rejection.reason());
        }
    }
}
