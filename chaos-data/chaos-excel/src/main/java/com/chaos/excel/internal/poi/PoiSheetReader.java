package com.chaos.excel.internal.poi;

import com.chaos.excel.ExcelProcessingException;
import com.chaos.excel.RowLimitExceededException;
import com.chaos.excel.mapping.CellValue;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

/**
 * 只读、流式的 XLSX Sheet 读取器。
 *
 * <p>直接读取 sheet XML 的 {@code c/v/t/s} 节点，保留原始类型，避免
 * {@code XSSFSheetXMLHandler} 只提供格式化字符串导致的类型丢失。</p>
 */
public final class PoiSheetReader {

    private PoiSheetReader() {
    }

    /**
     * 读取指定 Sheet，并按行回调。
     *
     * @param path XLSX 文件
     * @param sheetName Sheet 名
     * @param headerRows 表头行数，0 表示没有表头
     * @param maxRows 最大数据行数
     * @param maxColumns 最大列数
     * @param handler 行处理器
     */
    public static void read(
            Path path,
            String sheetName,
            int headerRows,
            int maxRows,
            int maxColumns,
            RowHandler handler) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(sheetName, "sheetName must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        if (headerRows < 0) {
            throw new IllegalArgumentException("headerRows must be at least 0");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be greater than 0");
        }
        if (maxColumns < 1) {
            throw new IllegalArgumentException("maxColumns must be greater than 0");
        }
        try (OPCPackage packageFile = OPCPackage.open(path.toFile())) {
            XSSFReader reader = new XSSFReader(packageFile);
            reader.setUseReadOnlySharedStringsTable(true);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = reader.getSheetIterator();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    if (!sheetName.equals(sheets.getSheetName())) {
                        continue;
                    }
                    parse(sheet, sharedStrings, styles, headerRows, maxRows, maxColumns, handler);
                    return;
                }
            }
            throw new ExcelProcessingException("Excel sheet not found: " + sheetName);
        } catch (ExcelProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExcelProcessingException("Excel sheet cannot be read: " + sheetName, exception);
        }
    }

    private static void parse(
            InputStream input,
            SharedStrings sharedStrings,
            StylesTable styles,
            int headerRows,
            int maxRows,
            int maxColumns,
            RowHandler handler) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(input);
        DataFormatter formatter = new DataFormatter();
        Map<Integer, String> header = new LinkedHashMap<>();
        boolean headerSent = false;
        int currentRow = -1;
        int nextRow = 0;
        int nextColumn = 0;
        int dataRows = 0;
        Map<Integer, CellValue> cells = new LinkedHashMap<>();
        boolean rowHasValue = false;
        CellState state = new CellState();
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    switch (localName) {
                        case "row" -> {
                            String rowRef = attribute(reader, "r");
                            currentRow = rowRef == null ? nextRow : parsePositiveInt(rowRef) - 1;
                            nextRow = currentRow + 1;
                            nextColumn = 0;
                            cells = new LinkedHashMap<>();
                            rowHasValue = false;
                        }
                        case "c" -> state.start(attribute(reader, "r"), attribute(reader, "t"),
                                parseStyle(attribute(reader, "s")), nextColumn);
                        case "v" -> state.insideValue = true;
                        case "f" -> {
                            state.formula = true;
                        }
                        case "is" -> {
                            state.inline = true;
                            state.inlineText.setLength(0);
                        }
                        case "t" -> {
                            if (state.inline) {
                                state.insideInlineText = true;
                            }
                        }
                        default -> {
                            // 其他节点不参与单元格解析
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    if (state.insideValue) {
                        state.value.append(reader.getTextCharacters(), reader.getTextStart(),
                                reader.getTextLength());
                    }
                    if (state.insideInlineText) {
                        state.inlineText.append(reader.getTextCharacters(), reader.getTextStart(),
                                reader.getTextLength());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    switch (localName) {
                        case "v" -> state.insideValue = false;
                        case "t" -> state.insideInlineText = false;
                        case "is" -> state.inline = false;
                        case "c" -> {
                            CellValue value = state.toCellValue(sharedStrings, styles, formatter);
                            int column = state.column;
                            if (column >= maxColumns) {
                                throw new ExcelProcessingException(
                                        "Excel column limit exceeded: " + maxColumns);
                            }
                            cells.put(column, value);
                            nextColumn = column + 1;
                            if (!value.isBlank()) {
                                rowHasValue = true;
                            }
                            state.reset();
                        }
                        case "row" -> {
                            if (currentRow < headerRows) {
                                if (rowHasValue) {
                                    for (Map.Entry<Integer, CellValue> entry : cells.entrySet()) {
                                        header.put(entry.getKey(), entry.getValue().displayValue());
                                    }
                                }
                                if (currentRow == headerRows - 1) {
                                    handler.header(Map.copyOf(header));
                                    headerSent = true;
                                }
                            } else if (rowHasValue) {
                                dataRows++;
                                if (dataRows > maxRows) {
                                    throw new RowLimitExceededException(maxRows);
                                }
                                handler.row(currentRow + 1, Map.copyOf(cells));
                            }
                        }
                        default -> {
                            // 其他节点不参与单元格解析
                        }
                    }
                }
            }
            if (headerRows > 0 && !headerSent) {
                handler.header(Map.copyOf(header));
            }
        } finally {
            reader.close();
        }
    }

    private static String attribute(XMLStreamReader reader, String name) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (name.equals(reader.getAttributeLocalName(index))) {
                return reader.getAttributeValue(index);
            }
        }
        return null;
    }

    private static int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ExcelProcessingException("Invalid Excel row number: " + value, exception);
        }
    }

    private static int parseStyle(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static int columnIndex(String reference) {
        if (reference == null || reference.isBlank()) {
            return -1;
        }
        int index = 0;
        for (int position = 0; position < reference.length(); position++) {
            char character = reference.charAt(position);
            if (character >= 'A' && character <= 'Z') {
                index = index * 26 + (character - 'A' + 1);
            } else if (character >= 'a' && character <= 'z') {
                index = index * 26 + (character - 'a' + 1);
            } else {
                break;
            }
        }
        return index - 1;
    }

    private static final class CellState {
        private final StringBuilder value = new StringBuilder();
        private final StringBuilder inlineText = new StringBuilder();
        private String type = "n";
        private int styleIndex;
        private int column;
        private boolean insideValue;
        private boolean insideInlineText;
        private boolean inline;
        private boolean formula;

        private void start(String reference, String cellType, int style, int fallbackColumn) {
            type = cellType == null ? "n" : cellType;
            styleIndex = style;
            int parsed = columnIndex(reference);
            column = parsed >= 0 ? parsed : fallbackColumn;
            value.setLength(0);
            inlineText.setLength(0);
            insideValue = false;
            insideInlineText = false;
            inline = false;
            formula = false;
        }

        private CellValue toCellValue(
                SharedStrings sharedStrings, StylesTable styles, DataFormatter formatter) {
            String raw = value.toString();
            if ("inlineStr".equals(type)) {
                return CellValue.text(inlineText.toString());
            }
            if ("s".equals(type)) {
                if (raw.isBlank()) {
                    return CellValue.blank();
                }
                try {
                    int index = Integer.parseInt(raw.trim());
                    if (index < 0 || index >= sharedStrings.getCount()) {
                        throw new ExcelProcessingException("Invalid shared string index: " + index);
                    }
                    return CellValue.text(sharedStrings.getItemAt(index).getString());
                } catch (NumberFormatException exception) {
                    throw new ExcelProcessingException("Invalid shared string index: " + raw, exception);
                }
            }
            if ("str".equals(type)) {
                return raw.isBlank()
                        ? (formula ? CellValue.formula() : CellValue.blank())
                        : CellValue.text(raw);
            }
            if ("b".equals(type)) {
                boolean booleanValue = "1".equals(raw.trim());
                return CellValue.bool(booleanValue, raw, booleanValue ? "TRUE" : "FALSE");
            }
            if ("e".equals(type)) {
                return CellValue.error(raw, raw);
            }
            if ("d".equals(type)) {
                return dateValue(raw);
            }
            if (raw.isBlank()) {
                return formula ? CellValue.formula() : CellValue.blank();
            }
            try {
                BigDecimal number = new BigDecimal(raw.trim());
                XSSFCellStyle style = styleAt(styles, styleIndex);
                String format = style == null ? null : style.getDataFormatString();
                short formatIndex = style == null ? 0 : style.getDataFormat();
                String effectiveFormat = format == null
                        ? BuiltinFormats.getBuiltinFormat(formatIndex) : format;
                String formatted = formatter.formatRawCellContents(
                        number.doubleValue(), formatIndex, effectiveFormat);
                if (DateUtil.isADateFormat(formatIndex, effectiveFormat)) {
                    return CellValue.date(
                            DateUtil.getLocalDateTime(number.doubleValue()), raw, formatted);
                }
                return CellValue.number(number, raw, formatted);
            } catch (NumberFormatException exception) {
                return CellValue.text(raw);
            }
        }

        private static XSSFCellStyle styleAt(StylesTable styles, int index) {
            try {
                return styles.getStyleAt(index);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private static CellValue dateValue(String raw) {
            if (raw == null || raw.isBlank()) {
                return CellValue.blank();
            }
            String text = raw.trim();
            try {
                return CellValue.date(LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        raw, text);
            } catch (DateTimeParseException exception) {
                try {
                    return CellValue.date(
                            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(),
                            raw, text);
                } catch (DateTimeParseException ignored) {
                    return CellValue.text(raw);
                }
            }
        }

        private void reset() {
            type = "n";
            styleIndex = 0;
            column = 0;
            value.setLength(0);
            inlineText.setLength(0);
            insideValue = false;
            insideInlineText = false;
            inline = false;
            formula = false;
        }
    }

    /** 逐行读取回调。 */
    public interface RowHandler {

        /**
         * 表头行读取完成。
         *
         * @param headers 列索引到表头文本的映射
         */
        default void header(Map<Integer, String> headers) {
        }

        /**
         * 读取到一条数据行。
         *
         * @param rowNumber 从 1 开始的 Excel 行号
         * @param cells 列索引到单元格值的映射
         */
        void row(int rowNumber, Map<Integer, CellValue> cells);
    }
}
