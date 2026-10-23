package com.chaos.excel.mapping;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 默认单元格转换器注册表。
 *
 * <p>转换顺序为：自定义转换器 → 字符串 → 数字 → 布尔 → 日期 → 枚举。
 * 未知类型直接抛出 {@link RowMappingException}，避免静默写入错误数据。</p>
 */
public final class ConverterRegistry {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private ConverterRegistry() {
    }

    /**
     * 将单元格值转换为目标字段类型。
     *
     * @param cell 单元格值
     * @param targetType 目标类型
     * @param column 列绑定
     * @return 字段值
     */
    public static Object convert(CellValue cell, Class<?> targetType, ColumnBinding column) {
        Objects.requireNonNull(cell, "cell must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        if (cell.kind() == CellKind.ERROR) {
            throw formatError(cell, column, "单元格为错误值");
        }
        if (cell.kind() == CellKind.FORMULA) {
            throw new RowMappingException(
                    RowMappingException.CODE_FORMULA_NO_CACHE,
                    column.name(),
                    column.index(),
                    null,
                    "公式单元格没有缓存值：" + column.name());
        }
        if (column.converterType() != AutoCellConverter.class) {
            return customConverter(cell, column);
        }
        if (targetType == String.class || targetType == CharSequence.class
                || targetType == Object.class) {
            return cell.displayValue();
        }
        if (targetType == BigDecimal.class) {
            return number(cell, column);
        }
        if (targetType == BigInteger.class) {
            return number(cell, column).toBigInteger();
        }
        if (targetType == int.class || targetType == Integer.class
                || targetType == long.class || targetType == Long.class
                || targetType == short.class || targetType == Short.class
                || targetType == byte.class || targetType == Byte.class) {
            return integral(cell, column, targetType);
        }
        if (targetType == double.class || targetType == Double.class) {
            return number(cell, column).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return number(cell, column).floatValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return bool(cell, column);
        }
        if (targetType == LocalDate.class) {
            return dateTime(cell, column).toLocalDate();
        }
        if (targetType == LocalDateTime.class) {
            return dateTime(cell, column);
        }
        if (targetType == Date.class) {
            return Date.from(dateTime(cell, column)
                    .atZone(ZoneId.systemDefault()).toInstant());
        }
        if (targetType == Instant.class) {
            return dateTime(cell, column).atZone(ZoneId.systemDefault()).toInstant();
        }
        if (targetType.isEnum()) {
            return enumValue(cell, column, targetType);
        }
        throw formatError(cell, column, "不支持的字段类型：" + targetType.getName());
    }

    private static Object customConverter(CellValue cell, ColumnBinding column) {
        try {
            Class<? extends CellConverter<?>> type = column.converterType();
            Constructor<? extends CellConverter<?>> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance().read(cell, column);
        } catch (ReflectiveOperationException exception) {
            throw new RowMappingException(
                    RowMappingException.CODE_FORMAT,
                    column.name(),
                    column.index(),
                    cell.displayValue(),
                    "自定义转换器实例化失败：" + column.converterType().getName(),
                    exception);
        }
    }

    private static Object integral(CellValue cell, ColumnBinding column, Class<?> targetType) {
        BigDecimal value = number(cell, column);
        try {
            if (targetType == int.class || targetType == Integer.class) {
                return value.intValueExact();
            }
            if (targetType == long.class || targetType == Long.class) {
                return value.longValueExact();
            }
            if (targetType == short.class || targetType == Short.class) {
                return value.shortValueExact();
            }
            return value.byteValueExact();
        } catch (ArithmeticException exception) {
            throw formatError(cell, column, "不是有效整数：" + cell.displayValue());
        }
    }

    private static BigDecimal number(CellValue cell, ColumnBinding column) {
        if (cell.numberValue() != null) {
            return cell.numberValue();
        }
        String value = cell.displayValue();
        if (value == null || value.isBlank()) {
            throw formatError(cell, column, "数字不能为空");
        }
        String normalized = value.trim().replace(",", "").replace(" ", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw formatError(cell, column, "不是有效数字：" + value);
        }
    }

    private static boolean bool(CellValue cell, ColumnBinding column) {
        if (cell.numberValue() != null) {
            return cell.numberValue().compareTo(BigDecimal.ZERO) != 0;
        }
        String value = cell.displayValue();
        if (value == null || value.isBlank()) {
            throw formatError(cell, column, "布尔值不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y", "是" -> true;
            case "false", "0", "no", "n", "否" -> false;
            default -> throw formatError(cell, column, "不是有效布尔值：" + value);
        };
    }

    private static LocalDateTime dateTime(CellValue cell, ColumnBinding column) {
        if (cell.dateTimeValue() != null) {
            return cell.dateTimeValue();
        }
        String value = cell.displayValue();
        if (value == null || value.isBlank()) {
            throw formatError(cell, column, "日期不能为空");
        }
        String text = value.trim();
        DateTimeFormatter configured = configuredFormatter(column.format());
        if (configured != null) {
            try {
                return LocalDateTime.parse(text, configured);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(text, configured).atStartOfDay();
                } catch (DateTimeParseException ignoredAgain) {
                    // 继续尝试内置格式
                }
            }
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        throw formatError(cell, column, "不是有效日期：" + value);
    }

    private static DateTimeFormatter configuredFormatter(String format) {
        if (format == null || format.isBlank()) {
            return null;
        }
        String pattern = format;
        if (!pattern.contains("HH") && !pattern.contains("hh") && !pattern.contains("ss")) {
            pattern = pattern.replace("mm", "MM");
        }
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Object enumValue(CellValue cell, ColumnBinding column, Class<?> targetType) {
        String value = cell.displayValue();
        if (value == null || value.isBlank()) {
            throw formatError(cell, column, "枚举值不能为空");
        }
        String text = value.trim();
        for (Object constant : targetType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw formatError(cell, column, "不是有效枚举值：" + value);
    }

    private static RowMappingException formatError(
            CellValue cell, ColumnBinding column, String message) {
        return new RowMappingException(
                RowMappingException.CODE_FORMAT,
                column.name(),
                column.index(),
                cell.displayValue(),
                column.name() + "：" + message);
    }
}
