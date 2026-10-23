package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.export.ExportOptions;
import com.chaos.excel.export.ExportSheet;
import com.chaos.excel.mapping.ColumnBinding;
import com.chaos.excel.mapping.RowMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * 基于 SXSSF 的多 Sheet 导出器。
 */
public final class PoiWorkbookWriter {

    private static final int DEFAULT_COLUMN_WIDTH = 20;
    private static final int MAX_COLUMN_WIDTH = 255;

    private PoiWorkbookWriter() {
    }

    /**
     * 将多个 Sheet 写入调用方输出流，不关闭该输出流。
     *
     * @param output 目标输出流
     * @param sheets 待导出的 Sheet
     * @param options 导出选项
     */
    public static void write(
            OutputStream output, List<ExportSheet> sheets, ExportOptions options) {
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(sheets, "sheets must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("sheets must not be empty");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(options.rowAccessWindowSize());
        workbook.setCompressTempFiles(true);
        try {
            Styles styles = new Styles(workbook);
            for (ExportSheet sheetDefinition : sheets) {
                writeSheet(workbook, styles, sheetDefinition, options);
            }
            workbook.write(output);
        } catch (IOException exception) {
            throw new ExcelProcessingException("Excel export failed", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof ExcelProcessingException processingException) {
                throw processingException;
            }
            throw new ExcelProcessingException("Excel export failed", exception);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 关闭失败不应覆盖已经抛出的导出异常
            }
        }
    }

    private static <T> void writeSheet(
            SXSSFWorkbook workbook,
            Styles styles,
            ExportSheet definition,
            ExportOptions options) {
        @SuppressWarnings("unchecked")
        RowMapper<T> mapper = (RowMapper<T>) RowMapper.of(definition.rowType());
        Sheet sheet = workbook.createSheet(definition.sheetName());
        List<ColumnBinding> columns = mapper.columns();
        int rowIndex = 0;
        if (options.includeHeader()) {
            Row header = sheet.createRow(rowIndex++);
            for (int index = 0; index < columns.size(); index++) {
                ColumnBinding column = columns.get(index);
                Cell cell = header.createCell(index);
                cell.setCellValue(column.name());
                cell.setCellStyle(styles.header);
                int width = Math.min(
                        MAX_COLUMN_WIDTH,
                        column.width() > 0 ? column.width() : DEFAULT_COLUMN_WIDTH);
                sheet.setColumnWidth(index, width * 256);
            }
            header.setHeightInPoints(30);
        }
        int lastRow = rowIndex - 1;
        for (Collection<?> batch : definition.batches()) {
            Objects.requireNonNull(batch, "batch must not be null");
            for (Object rowValue : batch) {
                Objects.requireNonNull(rowValue, "row must not be null");
                Row row = sheet.createRow(rowIndex++);
                List<Object> values = mapper.values(castRow(rowValue));
                for (int index = 0; index < columns.size(); index++) {
                    ColumnBinding column = columns.get(index);
                    Cell cell = row.createCell(index);
                    writeCell(workbook, styles, cell, values.get(index), column, options);
                }
                lastRow = rowIndex - 1;
            }
        }
        if (options.freezeHeader() && options.includeHeader()) {
            sheet.createFreezePane(0, 1);
        }
        if (options.autoFilter() && !columns.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(
                    0, Math.max(lastRow, 0), 0, columns.size() - 1));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T castRow(Object row) {
        return (T) row;
    }

    private static void writeCell(
            SXSSFWorkbook workbook,
            Styles styles,
            Cell cell,
            Object value,
            ColumnBinding column,
            ExportOptions options) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            cell.setCellValue(options.formulaSanitize() ? sanitize(text) : text);
            cell.setCellStyle(styles.body);
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(styles.body);
            return;
        }
        if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
            cell.setCellStyle(styles.body);
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(styles.date(column.format()));
            return;
        }
        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(styles.date(column.format()));
            return;
        }
        if (value instanceof Date date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date(column.format()));
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            cell.setCellValue(enumValue.name());
            cell.setCellStyle(styles.body);
            return;
        }
        cell.setCellValue(value.toString());
        cell.setCellStyle(styles.body);
    }

    private static String sanitize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private static final class Styles {
        private final SXSSFWorkbook workbook;
        private final CellStyle header;
        private final CellStyle body;
        private final Map<String, CellStyle> dates = new HashMap<>();

        private Styles(SXSSFWorkbook workbook) {
            this.workbook = workbook;
            this.header = createHeaderStyle(workbook);
            this.body = createBodyStyle(workbook);
        }

        @SuppressWarnings("deprecation")
        private CellStyle date(String format) {
            String pattern = format == null || format.isBlank() ? "yyyy-mm-dd" : format;
            return dates.computeIfAbsent(pattern, key -> {
                CellStyle style = workbook.createCellStyle();
                style.cloneStyleFrom(body);
                style.setDataFormat(workbook.createDataFormat().getFormat(key));
                return style;
            });
        }

        private static CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setFontName("宋体");
            font.setFontHeightInPoints((short) 12);
            font.setBold(true);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            borders(style);
            return style;
        }

        private static CellStyle createBodyStyle(SXSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setFontName("宋体");
            font.setFontHeightInPoints((short) 11);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
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
