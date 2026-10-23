package com.chaos.excel.mapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 与表格引擎无关的单元格值。
 *
 * <p>{@link #rawValue()} 保留单元格原始文本，{@link #formattedValue()} 保留 Excel
 * 显示文本。日期单元格同时提供 {@link #dateTimeValue()}，避免业务层按字符串反解析。</p>
 */
public record CellValue(
        CellKind kind,
        String rawValue,
        String formattedValue,
        BigDecimal numberValue,
        LocalDateTime dateTimeValue) {

    public CellValue {
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /** 创建空单元格。 */
    public static CellValue blank() {
        return new CellValue(CellKind.BLANK, null, null, null, null);
    }

    /** 创建字符串单元格。 */
    public static CellValue text(String value) {
        return new CellValue(CellKind.STRING, value, value, null, null);
    }

    /** 创建数字单元格。 */
    public static CellValue number(BigDecimal value, String rawValue, String formattedValue) {
        return new CellValue(CellKind.NUMBER,
                rawValue == null ? value.toPlainString() : rawValue,
                formattedValue, value, null);
    }

    /** 创建日期单元格。 */
    public static CellValue date(LocalDateTime value, String rawValue, String formattedValue) {
        return new CellValue(CellKind.DATE, rawValue, formattedValue, null, value);
    }

    /** 创建布尔单元格。 */
    public static CellValue bool(boolean value, String rawValue, String formattedValue) {
        return new CellValue(CellKind.BOOLEAN, rawValue, formattedValue, null, null);
    }

    /** 创建错误单元格。 */
    public static CellValue error(String rawValue, String formattedValue) {
        return new CellValue(CellKind.ERROR, rawValue, formattedValue, null, null);
    }

    /** 创建没有缓存值的公式单元格。 */
    public static CellValue formula() {
        return new CellValue(CellKind.FORMULA, null, null, null, null);
    }

    /** @return 单元格是否为空；没有缓存值的公式不算空，交由转换器报错 */
    public boolean isBlank() {
        if (kind == CellKind.BLANK) {
            return true;
        }
        return kind == CellKind.STRING
                && (rawValue == null || rawValue.isBlank())
                && (formattedValue == null || formattedValue.isBlank());
    }

    /** @return 用户可见的单元格文本，优先使用格式化值 */
    public String displayValue() {
        if (formattedValue != null) {
            return formattedValue;
        }
        if (rawValue != null) {
            return rawValue;
        }
        if (numberValue != null) {
            return numberValue.toPlainString();
        }
        if (dateTimeValue != null) {
            return dateTimeValue.toString();
        }
        return "";
    }
}
