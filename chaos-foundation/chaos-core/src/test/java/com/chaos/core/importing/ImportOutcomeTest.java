package com.chaos.core.importing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class ImportOutcomeTest {
    @Test
    void rollbackCountsOnlyActualFailuresAndZerosSuccesses() {
        var result = new ImportResult(10, 8, 2, List.of(), false, null);
        assertEquals(new ImportOutcome(10, 0, 2, null), ImportOutcome.rolledBack(result));
        assertEquals(new ImportOutcome(10, 10, 0, null), ImportOutcome.allSucceeded(10));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ImportOutcome.rolledBack(
                                new ImportResult(10, 10, 0, List.of(), false, null)));
    }
}
