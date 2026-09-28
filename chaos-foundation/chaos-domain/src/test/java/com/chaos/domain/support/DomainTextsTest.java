
package com.chaos.domain.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTextsTest {

    @Test
    void hasTextRejectsNullAndUnicodeWhitespace() {
        assertFalse(DomainTexts.hasText(null));
        assertFalse(DomainTexts.hasText(" \t\u2003 "));
        assertTrue(DomainTexts.hasText(" \u2003value "));
    }

    @Test
    void requireTextStripsUnicodeWhitespace() {
        assertEquals("value", DomainTexts.requireText("\u2003value\u2003",
                () -> new IllegalArgumentException("文本不能为空")));
    }

    @Test
    void requireTextUsesCallerProvidedException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DomainTexts.requireText(" \u2003 ", () -> new IllegalArgumentException("文本不能为空")));

        assertEquals("文本不能为空", exception.getMessage());
    }

    @Test
    void stripToNullNormalizesOptionalText() {
        assertNull(DomainTexts.stripToNull(null));
        assertNull(DomainTexts.stripToNull(" \u2003 "));
        assertEquals("value", DomainTexts.stripToNull(" value "));
    }

    @Test
    void normalizeDistinctFiltersAndPreservesFirstOccurrenceOrder() {
        assertEquals(List.of(), DomainTexts.normalizeDistinct(null));
        assertEquals(List.of("one", "two"),
                DomainTexts.normalizeDistinct(List.of(" one ", "", "two", "one", " \u2003 ")));
    }
}
