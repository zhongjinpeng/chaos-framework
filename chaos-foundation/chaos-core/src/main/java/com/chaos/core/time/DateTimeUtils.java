package com.chaos.core.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Objects;

/**
 * 当前时间读取、常用日期格式化、解析和时区转换工具。
 *
 * <p>实例方法统一从构造时注入的 {@link Clock} 读取当前时间，便于业务代码统一时区并进行确定性测试。静态方法默认支持
 * {@code yyyy-MM-dd}、{@code yyyy-MM-dd HH:mm:ss}、{@code yyyyMMdd} 和
 * {@code yyyyMMddHHmmss}，默认解析采用严格模式，不自动修正越界日期。
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter COMPACT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private final Clock clock;

    /**
     * 使用指定时钟创建日期时间工具实例。
     *
     * @param clock 提供当前瞬时时间和业务时区的非空时钟
     * @throws NullPointerException 时钟为空时抛出
     */
    public DateTimeUtils(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 使用系统时钟和指定业务时区创建日期时间工具实例。
     *
     * @param zoneId 用于解释本地日期和时间的非空业务时区
     * @return 使用系统时钟的日期时间工具实例
     * @throws NullPointerException 业务时区为空时抛出
     */
    public static DateTimeUtils system(ZoneId zoneId) {
        return new DateTimeUtils(Clock.system(Objects.requireNonNull(zoneId, "zoneId")));
    }

    /**
     * 读取当前瞬时时间，不受业务时区偏移影响。
     *
     * @return 当前时钟对应的瞬时时间
     */
    public Instant currentInstant() {
        return clock.instant();
    }

    /**
     * 按业务时区读取当前日期。
     *
     * @return 当前时钟和业务时区对应的本地日期
     */
    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    /**
     * 按业务时区读取当前日期时间。
     *
     * @return 当前时钟和业务时区对应的本地日期时间
     */
    public LocalDateTime currentDateTime() {
        return LocalDateTime.now(clock);
    }

    /**
     * 返回解释本地日期和时间所使用的业务时区。
     *
     * @return 当前时钟的时区
     */
    public ZoneId zoneId() {
        return clock.getZone();
    }

    /**
     * 将日期格式化为 {@code yyyy-MM-dd}。
     *
     * @param value 待格式化的非空日期
     * @return 标准日期文本
     * @throws NullPointerException 日期为空时抛出
     */
    public static String formatDate(LocalDate value) {
        return format(value, DATE_FORMATTER);
    }

    /**
     * 将日期时间格式化为 {@code yyyy-MM-dd HH:mm:ss}，不输出纳秒部分。
     *
     * @param value 待格式化的非空日期时间
     * @return 标准日期时间文本
     * @throws NullPointerException 日期时间为空时抛出
     */
    public static String formatDateTime(LocalDateTime value) {
        return format(value, DATE_TIME_FORMATTER);
    }

    /**
     * 将日期格式化为 {@code yyyyMMdd}。
     *
     * @param value 待格式化的非空日期
     * @return 紧凑日期文本
     * @throws NullPointerException 日期为空时抛出
     */
    public static String formatCompactDate(LocalDate value) {
        return format(value, COMPACT_DATE_FORMATTER);
    }

    /**
     * 将日期时间格式化为 {@code yyyyMMddHHmmss}，不输出纳秒部分。
     *
     * @param value 待格式化的非空日期时间
     * @return 紧凑日期时间文本
     * @throws NullPointerException 日期时间为空时抛出
     */
    public static String formatCompactDateTime(LocalDateTime value) {
        return format(value, COMPACT_DATE_TIME_FORMATTER);
    }

    /**
     * 使用指定格式器格式化日期或时间值。
     *
     * @param value 待格式化的非空时间值
     * @param formatter 非空格式器，必须支持该时间值包含的字段
     * @return 格式化后的文本
     * @throws NullPointerException 时间值或格式器为空时抛出
     */
    public static String format(TemporalAccessor value, DateTimeFormatter formatter) {
        Objects.requireNonNull(value, "value");
        return Objects.requireNonNull(formatter, "formatter").format(value);
    }

    /**
     * 将 {@code yyyy-MM-dd} 文本严格解析为日期。
     *
     * @param value 待解析的非空标准日期文本
     * @return 解析后的日期
     * @throws DateTimeParseException 文本格式错误或日期越界时抛出
     */
    public static LocalDate parseDate(String value) {
        return parseDate(value, DATE_FORMATTER);
    }

    /**
     * 将 {@code yyyy-MM-dd HH:mm:ss} 文本严格解析为日期时间。
     *
     * @param value 待解析的非空标准日期时间文本
     * @return 解析后的日期时间
     * @throws DateTimeParseException 文本格式错误或日期时间越界时抛出
     */
    public static LocalDateTime parseDateTime(String value) {
        return parseDateTime(value, DATE_TIME_FORMATTER);
    }

    /**
     * 将 {@code yyyyMMdd} 文本严格解析为日期。
     *
     * @param value 待解析的非空紧凑日期文本
     * @return 解析后的日期
     * @throws DateTimeParseException 文本格式错误或日期越界时抛出
     */
    public static LocalDate parseCompactDate(String value) {
        return parseDate(value, COMPACT_DATE_FORMATTER);
    }

    /**
     * 将 {@code yyyyMMddHHmmss} 文本严格解析为日期时间。
     *
     * @param value 待解析的非空紧凑日期时间文本
     * @return 解析后的日期时间
     * @throws DateTimeParseException 文本格式错误或日期时间越界时抛出
     */
    public static LocalDateTime parseCompactDateTime(String value) {
        return parseDateTime(value, COMPACT_DATE_TIME_FORMATTER);
    }

    /**
     * 使用指定格式器将文本解析为日期。
     *
     * @param value 待解析的非空日期文本
     * @param formatter 非空日期格式器，解析规则由调用方提供
     * @return 解析后的日期
     * @throws DateTimeParseException 文本不符合指定格式或日期越界时抛出
     * @throws NullPointerException 文本或格式器为空时抛出
     */
    public static LocalDate parseDate(String value, DateTimeFormatter formatter) {
        Objects.requireNonNull(value, "value");
        return LocalDate.parse(value, Objects.requireNonNull(formatter, "formatter"));
    }

    /**
     * 使用指定格式器将文本解析为日期时间。
     *
     * @param value 待解析的非空日期时间文本
     * @param formatter 非空日期时间格式器，解析规则由调用方提供
     * @return 解析后的日期时间
     * @throws DateTimeParseException 文本不符合指定格式或日期时间越界时抛出
     * @throws NullPointerException 文本或格式器为空时抛出
     */
    public static LocalDateTime parseDateTime(String value, DateTimeFormatter formatter) {
        Objects.requireNonNull(value, "value");
        return LocalDateTime.parse(value, Objects.requireNonNull(formatter, "formatter"));
    }

    /**
     * 按指定时区将瞬时时间转换为本地日期时间。
     *
     * @param instant 待转换的非空瞬时时间
     * @param zoneId 用于解释本地时间的非空时区
     * @return 对应时区下的本地日期时间
     * @throws NullPointerException 瞬时时间或时区为空时抛出
     */
    public static LocalDateTime toLocalDateTime(Instant instant, ZoneId zoneId) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(instant, "instant"), Objects.requireNonNull(zoneId, "zoneId"));
    }

    /**
     * 按指定时区将本地日期时间转换为瞬时时间。
     *
     * <p>夏令时重叠或间隙的解析规则与 {@link LocalDateTime#atZone(ZoneId)} 一致。
     *
     * @param value 待转换的非空本地日期时间
     * @param zoneId 用于解释本地时间的非空时区
     * @return 对应的瞬时时间
     * @throws NullPointerException 日期时间或时区为空时抛出
     */
    public static Instant toInstant(LocalDateTime value, ZoneId zoneId) {
        return Objects.requireNonNull(value, "value")
                .atZone(Objects.requireNonNull(zoneId, "zoneId"))
                .toInstant();
    }
}
