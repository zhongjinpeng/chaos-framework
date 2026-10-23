package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.internal.FileNames;
import com.chaos.excel.mapping.ColumnBinding;
import com.chaos.excel.mapping.RowMapper;
import com.chaos.excel.template.ColumnSpec;
import com.chaos.excel.template.SamplePlacement;
import com.chaos.excel.template.TemplateOptions;
import com.chaos.excel.template.TemplateSheet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * 基于 XSSFWorkbook 的模板生成器。
 */
public final class PoiTemplateWriter {

    private static final int DEFAULT_WIDTH = 20;
    private static final int MAX_WIDTH = 255;
    private static final String DICTIONARY_SHEET = "_chaos_dict";

    private PoiTemplateWriter() {
    }

    /**
     * 生成模板工作簿。
     *
     * @param sheets 数据 Sheet 定义
     * @param options 模板选项
     * @return XLSX 字节
     */
    public static byte[] write(List<TemplateSheet> sheets, TemplateOptions options) {
        Objects.requireNonNull(sheets, "sheets must not be null");
        Objects.requireNonNull(options, "options must not be null");
        if (sheets.isEmpty()) {
            throw new IllegalArgumentException("sheets must not be empty");
        }
        if (sheets.size() > options.maxSheets()) {
            throw new IllegalArgumentException("Too many template sheets: " + sheets.size());
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> sheetNames = new LinkedHashSet<>();
            List<SheetContext> contexts = new ArrayList<>();
            for (TemplateSheet definition : sheets) {
                SheetContext context = writeDataSheet(
                        workbook, styles, definition, options, sheetNames);
                contexts.add(context);
                if (options.samplePlacement() == SamplePlacement.SEPARATE_SHEET
                        && !definition.samples().isEmpty()) {
                    writeSampleSheet(workbook, styles, definition, options, sheetNames);
                }
            }
            Map<String, String> dropdownNames = writeDictionary(workbook, contexts, sheetNames);
            for (SheetContext context : contexts) {
                addValidations(context, options, dropdownNames);
            }
            if (options.includeInstructions()) {
                writeInstructions(workbook, styles, contexts, options, sheetNames);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ExcelProcessingException("Excel template creation failed", exception);
        }
    }

    private static SheetContext writeDataSheet(
            XSSFWorkbook workbook,
            Styles styles,
            TemplateSheet definition,
            TemplateOptions options,
            Set<String> sheetNames) {
        String sheetName = FileNames.sheetName(definition.sheetName(), sheetNames);
        sheetNames.add(sheetName);
        Sheet sheet = workbook.createSheet(sheetName);
        RowMapper<?> mapper = RowMapper.of(definition.rowType());
        List<ColumnBinding> columns = applyColumns(mapper, definition);
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            ColumnBinding column = columns.get(index);
            Cell cell = header.createCell(index);
            cell.setCellValue(column.required() ? column.name() + " *" : column.name());
            cell.setCellStyle(styles.header);
            sheet.setColumnWidth(index, width(column));
        }
        header.setHeightInPoints(30);
        if (options.samplePlacement() == SamplePlacement.INLINE) {
            writeSamples(sheet, mapper, columns, definition.samples(), options.headerRows(),
                    options.maxSampleRows(), styles);
        }
        sheet.createFreezePane(0, options.headerRows());
        return new SheetContext(definition, sheet, columns);
    }

    private static List<ColumnBinding> applyColumns(RowMapper<?> mapper, TemplateSheet definition) {
        List<ColumnBinding> columns = new ArrayList<>(mapper.columns());
        for (ColumnSpec override : definition.columns()) {
            boolean replaced = false;
            for (int index = 0; index < columns.size(); index++) {
                ColumnBinding current = columns.get(index);
                if (current.index() == override.index()) {
                    columns.set(index, current.withMetadata(override));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                throw new IllegalArgumentException(
                        "Template column index " + override.index()
                                + " does not exist on " + definition.rowType().getName());
            }
        }
        columns.sort((left, right) -> Integer.compare(left.index(), right.index()));
        return List.copyOf(columns);
    }

    private static void writeSampleSheet(
            XSSFWorkbook workbook,
            Styles styles,
            TemplateSheet definition,
            TemplateOptions options,
            Set<String> sheetNames) {
        if (definition.samples().size() > options.maxSampleRows()) {
            throw new IllegalArgumentException(
                    "Too many sample rows for sheet " + definition.sheetName());
        }
        String name = FileNames.sheetName("示例-" + definition.sheetName(), sheetNames);
        sheetNames.add(name);
        Sheet sheet = workbook.createSheet(name);
        RowMapper<?> mapper = RowMapper.of(definition.rowType());
        List<ColumnBinding> columns = applyColumns(mapper, definition);
        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            ColumnBinding column = columns.get(index);
            Cell cell = header.createCell(index);
            cell.setCellValue(column.name());
            cell.setCellStyle(styles.header);
            sheet.setColumnWidth(index, width(column));
        }
        writeSamples(sheet, mapper, columns, definition.samples(), 1,
                options.maxSampleRows(), styles);
        sheet.createFreezePane(0, 1);
    }

    private static <T> void writeSamples(
            Sheet sheet,
            RowMapper<T> mapper,
            List<ColumnBinding> columns,
            List<?> samples,
            int startRow,
            int maxSampleRows,
            Styles styles) {
        if (samples.size() > maxSampleRows) {
            throw new IllegalArgumentException("Too many sample rows: " + samples.size());
        }
        int rowIndex = startRow;
        for (Object sample : samples) {
            Row row = sheet.createRow(rowIndex++);
            List<Object> values = mapper.values(cast(sample));
            for (int index = 0; index < columns.size(); index++) {
                Cell cell = row.createCell(index);
                writeValue(cell, values.get(index), columns.get(index), styles);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static void writeValue(
            Cell cell, Object value, ColumnBinding column, Styles styles) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            cell.setCellValue(text);
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

    private static int width(ColumnBinding column) {
        int value = column.width() > 0 ? column.width() : DEFAULT_WIDTH;
        return Math.min(value, MAX_WIDTH) * 256;
    }

    private static Map<String, String> writeDictionary(
            XSSFWorkbook workbook, List<SheetContext> contexts, Set<String> sheetNames) {
        Map<String, List<String>> optionsByKey = new LinkedHashMap<>();
        for (SheetContext context : contexts) {
            for (ColumnBinding column : context.columns()) {
                if (column.dropdown().isBlank()) {
                    continue;
                }
                List<String> options = context.definition().dropdowns().get(column.dropdown());
                if (options == null) {
                    throw new IllegalArgumentException(
                            "Missing dropdown definition: " + column.dropdown());
                }
                List<String> existing = optionsByKey.putIfAbsent(column.dropdown(), options);
                if (existing != null && !existing.equals(options)) {
                    throw new IllegalArgumentException(
                            "Dropdown definition conflict: " + column.dropdown());
                }
            }
        }
        if (optionsByKey.isEmpty()) {
            return Map.of();
        }
        String name = FileNames.sheetName(DICTIONARY_SHEET, sheetNames);
        sheetNames.add(name);
        Sheet dictionary = workbook.createSheet(name);
        Map<String, String> names = new LinkedHashMap<>();
        int columnIndex = 0;
        for (Map.Entry<String, List<String>> entry : optionsByKey.entrySet()) {
            if (entry.getValue().isEmpty()) {
                columnIndex++;
                continue;
            }
            for (int rowIndex = 0; rowIndex < entry.getValue().size(); rowIndex++) {
                Row row = dictionary.getRow(rowIndex);
                if (row == null) {
                    row = dictionary.createRow(rowIndex);
                }
                row.createCell(columnIndex).setCellValue(entry.getValue().get(rowIndex));
            }
            String rangeName = dropdownName(entry.getKey());
            Name namedRange = workbook.createName();
            namedRange.setNameName(rangeName);
            String columnName = columnName(columnIndex);
            namedRange.setRefersToFormula("'" + name.replace("'", "''") + "'!$"
                    + columnName + "$1:$" + columnName + "$" + entry.getValue().size());
            names.put(entry.getKey(), rangeName);
            columnIndex++;
        }
        workbook.setSheetHidden(workbook.getSheetIndex(dictionary), true);
        return names;
    }

    private static void addValidations(
            SheetContext context, TemplateOptions options, Map<String, String> dropdownNames) {
        Sheet sheet = context.sheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (ColumnBinding column : context.columns()) {
            if (column.dropdown().isBlank()) {
                continue;
            }
            String rangeName = dropdownNames.get(column.dropdown());
            if (rangeName == null) {
                continue;
            }
            DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeName);
            CellRangeAddressList cells = new CellRangeAddressList(
                    options.headerRows(), options.lastDataRow(), column.index(), column.index());
            DataValidation validation = helper.createValidation(constraint, cells);
            validation.setEmptyCellAllowed(!column.required());
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("输入有误", "请从下拉列表中选择有效值");
            validation.setShowPromptBox(true);
            String prompt = column.hint().isBlank() ? "请选择列表中的有效值" : column.hint();
            validation.createPromptBox("请选择", prompt);
            sheet.addValidationData(validation);
        }
    }

    private static void writeInstructions(
            XSSFWorkbook workbook,
            Styles styles,
            List<SheetContext> contexts,
            TemplateOptions options,
            Set<String> sheetNames) {
        String name = FileNames.sheetName("填写说明", sheetNames);
        sheetNames.add(name);
        Sheet sheet = workbook.createSheet(name);
        int rowIndex = 0;
        Row title = sheet.createRow(rowIndex++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("填写说明");
        titleCell.setCellStyle(styles.title);
        rowIndex = writeInstructionRow(sheet, rowIndex, "表头行数", String.valueOf(options.headerRows()));
        rowIndex = writeInstructionRow(sheet, rowIndex, "示例数据",
                options.samplePlacement() == SamplePlacement.SEPARATE_SHEET
                        ? "见 示例-* Sheet" : "数据页表头之后");
        rowIndex++;
        for (SheetContext context : contexts) {
            rowIndex = writeInstructionRow(sheet, rowIndex, "Sheet", context.definition().sheetName());
            if (!context.definition().instructions().isBlank()) {
                rowIndex = writeInstructionRow(
                        sheet, rowIndex, "说明", context.definition().instructions());
            }
            for (ColumnBinding column : context.columns()) {
                String description = column.name()
                        + (column.required() ? "（必填）" : "")
                        + (column.format().isBlank() ? "" : " 格式：" + column.format())
                        + (column.dropdown().isBlank() ? "" : " 下拉：" + column.dropdown());
                rowIndex = writeInstructionRow(sheet, rowIndex, "列", description);
            }
            rowIndex++;
        }
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 80 * 256);
    }

    private static int writeInstructionRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private static String dropdownName(String key) {
        String safe = key.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isBlank()) {
            safe = "dropdown";
        }
        if (!Character.isLetter(safe.charAt(0)) && safe.charAt(0) != '_') {
            safe = "dd_" + safe;
        }
        String name = "chaos_dd_" + safe + "_" + Integer.toHexString(key.hashCode());
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String columnName(int zeroBasedColumn) {
        StringBuilder result = new StringBuilder();
        int value = zeroBasedColumn + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            result.append((char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return result.reverse().toString();
    }

    private record SheetContext(
            TemplateSheet definition, Sheet sheet, List<ColumnBinding> columns) {
    }

    private static final class Styles {
        private final XSSFWorkbook workbook;
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle title;
        private final Map<String, CellStyle> dates = new HashMap<>();

        private Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            this.header = headerStyle(workbook);
            this.body = bodyStyle(workbook);
            this.title = titleStyle(workbook);
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

        private static CellStyle headerStyle(XSSFWorkbook workbook) {
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

        private static CellStyle bodyStyle(XSSFWorkbook workbook) {
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

        private static CellStyle titleStyle(XSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setFontName("宋体");
            font.setFontHeightInPoints((short) 14);
            font.setBold(true);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
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
