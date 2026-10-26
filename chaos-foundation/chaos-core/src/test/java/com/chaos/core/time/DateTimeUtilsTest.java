package com.chaos.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class DateTimeUtilsTest {

    private static final Instant INSTANT = Instant.parse("2026-10-17T00:09:10Z");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 10, 17, 8, 9, 10);

    @Test
    void shouldReadCurrentValuesFromInjectedClock() {
        DateTimeUtils dateTimeUtils = new DateTimeUtils(Clock.fixed(INSTANT, ZONE_ID));

        assertEquals(INSTANT, dateTimeUtils.currentInstant());
        assertEquals(DATE, dateTimeUtils.currentDate());
        assertEquals(DATE_TIME, dateTimeUtils.currentDateTime());
        assertEquals(ZONE_ID, dateTimeUtils.zoneId());
    }

    @Test
    void shouldFormatAndParseCommonFormats() {
        assertEquals("2026-10-17", DateTimeUtils.formatDate(DATE));
        assertEquals(DATE, DateTimeUtils.parseDate("2026-10-17"));
        assertEquals("2026-10-17 08:09:10", DateTimeUtils.formatDateTime(DATE_TIME));
        assertEquals(DATE_TIME, DateTimeUtils.parseDateTime("2026-10-17 08:09:10"));
        assertEquals("20261017", DateTimeUtils.formatCompactDate(DATE));
        assertEquals(DATE, DateTimeUtils.parseCompactDate("20261017"));
        assertEquals("20261017080910", DateTimeUtils.formatCompactDateTime(DATE_TIME));
        assertEquals(DATE_TIME, DateTimeUtils.parseCompactDateTime("20261017080910"));
    }

    @Test
    void shouldSupportCallerProvidedFormatter() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");

        assertEquals("17/10/2026", DateTimeUtils.format(DATE, formatter));
        assertEquals(DATE, DateTimeUtils.parseDate("17/10/2026", formatter));
    }

    @Test
    void shouldRejectInvalidDefaultDateInsteadOfNormalizingIt() {
        assertThrows(DateTimeParseException.class, () -> DateTimeUtils.parseDate("2026-02-30"));
        assertThrows(
                DateTimeParseException.class,
                () -> DateTimeUtils.parseDateTime("2026-02-30 08:00:00"));
    }

    @Test
    void shouldConvertInstantUsingExplicitZone() {
        assertEquals(DATE_TIME, DateTimeUtils.toLocalDateTime(INSTANT, ZONE_ID));
        assertEquals(INSTANT, DateTimeUtils.toInstant(DATE_TIME, ZONE_ID));
    }

    @Test
    void shouldRejectNullClockAndZone() {
        assertThrows(NullPointerException.class, () -> new DateTimeUtils(null));
        assertThrows(NullPointerException.class, () -> DateTimeUtils.system(null));
    }
}
