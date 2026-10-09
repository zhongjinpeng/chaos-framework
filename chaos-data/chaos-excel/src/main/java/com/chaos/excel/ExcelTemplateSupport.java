package com.chaos.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Shared XLSX template styling and dropdown support.
 *
 * <p>Business modules declare sheet appearance and dropdown dictionaries; this strategy owns the
 * workbook-specific implementation, including hidden dictionary sheets and named ranges.
 */
public final class ExcelTemplateSupport {
    public static final String DICTIONARY_SHEET = "_字典";
    public static final int DEFAULT_FIRST_DATA_ROW = 1;
    public static final int DEFAULT_LAST_DATA_ROW = 4999;

    private ExcelTemplateSupport() {
    }

    /** A sheet's reusable visual and validation definition. */
    public record SheetDefinition(String sheetName, int[] columnWidths,
                                  Map<Integer, Dropdown> dropdowns) {
        public SheetDefinition {
            if (sheetName == null || sheetName.isBlank()) {
                throw new IllegalArgumentException("sheetName must not be blank");
            }
            columnWidths = columnWidths == null ? new int[0] : columnWidths.clone();
            dropdowns = dropdowns == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(dropdowns));
        }

        public SheetDefinition(String sheetName, int[] columnWidths) {
            this(sheetName, columnWidths, Map.of());
        }
    }

    /** One dropdown column backed by a named range. */
    public record Dropdown(String name, List<String> options, String prompt) {
        public Dropdown {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("dropdown name must not be blank");
            }
            name = normalizeName(name);
            options = options == null
                    ? List.of()
                    : options.stream().filter(value -> value != null && !value.isBlank()).toList();
            prompt = prompt == null || prompt.isBlank() ? "请选择列表中的有效值" : prompt;
        }

        public Dropdown(String name, List<String> options) {
            this(name, options, "请选择列表中的有效值");
        }
    }

    /** Apply the common workbook behavior to a workbook already written by FastExcel. */
    public static byte[] enhance(byte[] content, List<SheetDefinition> definitions) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions must not be empty");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Map<String, Integer> dictionaryColumns = writeDictionary(workbook, definitions);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook, bodyStyle);
            for (SheetDefinition definition : definitions) {
                Sheet sheet = workbook.getSheet(definition.sheetName());
                if (sheet == null) {
                    throw new IllegalArgumentException("Sheet not found: " + definition.sheetName());
                }
                styleSheet(sheet, definition.columnWidths(), headerStyle, bodyStyle, dateStyle);
                addDropdowns(sheet, definition.dropdowns(), dictionaryColumns);
            }
            int dictionaryIndex = workbook.getSheetIndex(DICTIONARY_SHEET);
            if (dictionaryIndex >= 0) {
                workbook.setSheetHidden(dictionaryIndex, true);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ExcelProcessingException("Excel template enhancement failed", exception);
        }
    }

    private static Map<String, Integer> writeDictionary(XSSFWorkbook workbook,
                                                          List<SheetDefinition> definitions) {
        Sheet dictionary = workbook.getSheet(DICTIONARY_SHEET);
        Map<String, Dropdown> unique = new LinkedHashMap<>();
        for (SheetDefinition definition : definitions) {
            for (Dropdown dropdown : definition.dropdowns().values()) {
                if (!dropdown.options().isEmpty()) {
                    unique.putIfAbsent(dropdown.name(), dropdown);
                }
            }
        }

        if (unique.isEmpty()) {
            return Map.of();
        }
        if (dictionary == null) {
            dictionary = workbook.createSheet(DICTIONARY_SHEET);
        }

        Map<String, Integer> columns = new LinkedHashMap<>();
        int column = 0;
        for (Dropdown dropdown : unique.values()) {
            columns.put(dropdown.name(), column);
            for (int rowIndex = 0; rowIndex < dropdown.options().size(); rowIndex++) {
                Row row = dictionary.getRow(rowIndex);
                if (row == null) {
                    row = dictionary.createRow(rowIndex);
                }
                row.createCell(column).setCellValue(dropdown.options().get(rowIndex));
            }
            Name name = workbook.getName(dropdown.name());
            if (name == null) {
                name = workbook.createName();
            }
            name.setNameName(dropdown.name());
            String columnName = toColumnName(column);
            name.setRefersToFormula("'" + DICTIONARY_SHEET + "'!$" + columnName
                    + "$1:$" + columnName + "$" + dropdown.options().size());
            column++;
        }
        return columns;
    }

    private static void addDropdowns(Sheet sheet, Map<Integer, Dropdown> dropdowns,
                                     Map<String, Integer> dictionaryColumns) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (Map.Entry<Integer, Dropdown> entry : dropdowns.entrySet()) {
            Dropdown dropdown = entry.getValue();
            if (entry.getKey() == null || dropdown == null || dropdown.options().isEmpty()
                    || !dictionaryColumns.containsKey(dropdown.name())) {
                continue;
            }
            DataValidationConstraint constraint = helper.createFormulaListConstraint(dropdown.name());
            CellRangeAddressList cells = new CellRangeAddressList(
                    DEFAULT_FIRST_DATA_ROW, DEFAULT_LAST_DATA_ROW, entry.getKey(), entry.getKey());
            DataValidation validation = helper.createValidation(constraint, cells);
            validation.setEmptyCellAllowed(true);
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("输入有误", "请从下拉列表中选择有效值");
            validation.setShowPromptBox(true);
            validation.createPromptBox("请选择", dropdown.prompt());
            sheet.addValidationData(validation);
        }
    }

    private static void styleSheet(Sheet sheet, int[] widths, CellStyle headerStyle,
                                   CellStyle bodyStyle, CellStyle dateStyle) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return;
        }
        header.setHeightInPoints(36);
        for (int column = 0; column < widths.length; column++) {
            Cell cell = header.getCell(column);
            if (cell != null) {
                cell.setCellStyle(headerStyle);
            }
            if (widths[column] > 0) {
                sheet.setColumnWidth(column, widths[column] * 256);
            }
        }
        Row sample = sheet.getRow(1);
        if (sample != null) {
            sample.setHeightInPoints(22);
            for (int column = 0; column < widths.length; column++) {
                Cell cell = sample.getCell(column);
                if (cell != null) {
                    String format = cell.getCellStyle().getDataFormatString();
                    cell.setCellStyle(format != null && format.contains("yy") ? dateStyle : bodyStyle);
                }
            }
        }
        sheet.createFreezePane(0, 1);
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook workbook) {
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
        setBorders(style);
        return style;
    }

    private static CellStyle createBodyStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 11);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setBorders(style);
        return style;
    }

    private static void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private static CellStyle createDateStyle(XSSFWorkbook workbook, CellStyle bodyStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(bodyStyle);
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private static String toColumnName(int zeroBasedColumn) {
        StringBuilder result = new StringBuilder();
        int value = zeroBasedColumn + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            result.append((char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return result.reverse().toString();
    }

    private static String normalizeName(String raw) {
        String safe = raw.replaceAll("[^a-zA-Z0-9_]", "_");
        if (safe.isBlank()) {
            safe = "dropdown";
        }
        if (!Character.isLetter(safe.charAt(0)) && safe.charAt(0) != '_') {
            safe = "dd_" + safe;
        }
        return safe.length() > 200 ? safe.substring(0, 200) : safe;
    }
}
