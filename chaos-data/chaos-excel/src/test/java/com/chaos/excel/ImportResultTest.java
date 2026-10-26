package com.chaos.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.chaos.core.importing.ImportRowError;
import com.chaos.excel.importer.ImportResult;
import com.chaos.excel.importer.SheetResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImportResultTest {

    @Test
    void shouldExposeDerivedStatisticsAndCacheErrorFile() {
        ImportRowError error = new ImportRowError(
                "试剂", 3, 1, "单位", "EXCEL_ENUM", "单位不在下拉列表中",
                Map.of(0, "氯化钠", 1, "kg"));
        ImportResult result = new ImportResult(
                "records.xlsx",
                2,
                1,
                1,
                List.of(error),
                false,
                Map.of("试剂", new SheetResult("试剂", 2, 1, 1, false)),
                Map.of("试剂", List.of("名称", "单位")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isPartialSuccess()).isTrue();
        assertThat(result.successRate()).isEqualTo(0.5);
        assertThat(result.collectedErrorCount()).isEqualTo(1);
        assertThat(result.errorsForSheet("试剂")).containsExactly(error);
        assertThat(result.errorsForSheet("仪器")).isEmpty();
        assertThat(result.sheet("试剂")).isPresent();
        assertThat(result.sheet("仪器")).isEmpty();

        Optional<ExcelFile> first = result.errorFile();
        Optional<ExcelFile> second = result.errorFile();
        assertThat(first).isPresent();
        assertThat(second).isSameAs(first);
    }

    @Test
    void shouldTreatEmptyImportAsSuccessWithoutErrorFile() {
        ImportResult result = new ImportResult(
                "empty.xlsx", 0, 0, 0, List.of(), false, Map.of(), Map.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isPartialSuccess()).isFalse();
        assertThat(result.successRate()).isEqualTo(1.0);
        assertThat(result.errorFile()).isEmpty();
    }
}
