package com.chaos.excel.error;

import com.chaos.excel.ExcelDocument;
import com.chaos.excel.ExcelFile;
import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.importer.ImportError;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.internal.FileNames;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 将导入错误渲染为可直接下载的 XLSX 文件。
 */
public final class ImportErrorReport {

    private static final int MAX_WIDTH = 255;

    private ImportErrorReport() {
    }

    /**
     * 创建错误文件。
     *
     * @param result 导入结果
     * @return 没有错误时返回空
     */
    public static Optional<ExcelFile> create(ImportResult result) {
        if (result.errors().isEmpty()) {
            return Optional.empty();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Map<String, List<ImportError>> bySheet = groupBySheet(result.errors());
            Set<String> sheetNames = new LinkedHashSet<>();
            for (Map.Entry<String, List<ImportError>> entry : bySheet.entrySet()) {
                writeSheet(workbook, styles, entry.getKey(), entry.getValue(),
                        result.sheetHeaders().getOrDefault(entry.getKey(), List.of()), sheetNames);
            }
            workbook.write(output);
            String filename = FileNames.timestamped(
                    FileNames.baseName(result.originalFilename()), "-导入错误");
            return Optional.of(ExcelDocument.xlsx(filename, output.toByteArray()));
        } catch (IOException exception) {
            throw new ExcelProcessingException("Excel error report creation failed", exception);
        }
    }

    private static Map<String, List<ImportError>> groupBySheet(List<ImportError> errors) {
        Map<String, List<ImportError>> grouped = new LinkedHashMap<>();
        for (ImportError error : errors) {
            grouped.computeIfAbsent(error.sheetName(), key -> new ArrayList<>()).add(error);
        }
        return grouped;
    }

    private static void writeSheet(
            XSSFWorkbook workbook,
            Styles styles,
            String sourceSheet,
            List<ImportError> errors,
            List<String> headers,
            Set<String> sheetNames) {
        String sheetName = FileNames.sheetName("错误-" + sourceSheet, sheetNames);
        sheetNames.add(sheetName);
        Sheet sheet = workbook.createSheet(sheetName);
        int columnCount = 2 + headers.size() + 3;
        Row header = sheet.createRow(0);
        int column = 0;
        writeHeader(header, column++, "行号", styles.header);
        writeHeader(header, column++, "工作表", styles.header);
        for (String name : headers) {
            writeHeader(header, column++, name, styles.header);
        }
        writeHeader(header, column++, "错误字段", styles.header);
        writeHeader(header, column++, "错误原因", styles.header);
        writeHeader(header, column, "错误码", styles.header);
        int rowIndex = 1;
        for (ImportError error : errors) {
            Row row = sheet.createRow(rowIndex++);
            int cell = 0;
            row.createCell(cell++).setCellValue(error.rowNumber());
            row.createCell(cell++).setCellValue(sourceSheet);
            for (int index = 0; index < headers.size(); index++) {
                Cell value = row.createCell(cell++);
                value.setCellValue(error.rawCells().getOrDefault(index, ""));
                value.setCellStyle(styles.body);
            }
            Cell field = row.createCell(cell++);
            field.setCellValue(error.columnName() == null ? "" : error.columnName());
            field.setCellStyle(styles.body);
            Cell reason = row.createCell(cell++);
            reason.setCellValue(error.message());
            reason.setCellStyle(styles.error);
            Cell code = row.createCell(cell);
            code.setCellValue(error.code());
            code.setCellStyle(styles.body);
        }
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < columnCount; index++) {
            int width = switch (index) {
                case 0 -> 8;
                case 1 -> 12;
                case 2 -> 20;
                default -> 18;
            };
            if (index == columnCount - 2) {
                width = 40;
            }
            sheet.setColumnWidth(index, Math.min(width, MAX_WIDTH) * 256);
        }
        header.setHeightInPoints(30);
    }

    private static void writeHeader(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle error;

        private Styles(XSSFWorkbook workbook) {
            this.header = headerStyle(workbook);
            this.body = bodyStyle(workbook);
            this.error = workbook.createCellStyle();
            this.error.cloneStyleFrom(body);
            Font font = workbook.createFont();
            font.setColor(IndexedColors.RED.getIndex());
            this.error.setFont(font);
        }

        private static CellStyle headerStyle(XSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            borders(style);
            return style;
        }

        private static CellStyle bodyStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            borders(style);
            return style;
        }

        private static void borders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
        }
    }
}
